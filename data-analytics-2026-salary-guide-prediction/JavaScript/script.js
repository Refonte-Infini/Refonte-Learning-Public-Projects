const axios = require("axios");
const fs = require("fs");
const { ChartJSNodeCanvas } = require("chartjs-node-canvas");

// ============================================================
// LEGIT DATA SOURCE (USED LIVE AT RUNTIME)
// ============================================================
// We fetch wage percentiles from O*NET OnLine "Save Table: CSV" export,
// which corresponds to the National Wages table on:
// https://www.onetonline.org/link/localwages/{code}
//
// O*NET wage pages show they use BLS wage data (e.g., "Bureau of Labor Statistics 2024 wage data").
// We use annual percentiles from CSV:
// - Annual Low (10%)    -> Entry proxy
// - Annual Median (50%) -> Mid proxy
// - Annual High (90%)   -> Senior proxy
// ============================================================

const ONET_CSV_URL = (code) =>
  `https://www.onetonline.org/link/localwagestable/${code}/LocalWages_${code}_US.csv?fmt=csv`;

const ONET_PAGE_URL = (code) =>
  `https://www.onetonline.org/link/localwages/${code}`;

// Where the data you took is defined (role -> official O*NET code)
const ROLE_TO_ONET = {
  "Data Analyst (proxy: Operations Research Analysts)": "15-2031.00",
  "Business Intelligence Analyst": "15-2051.01",
  "Data Scientist": "15-2051.00",
  "Data Engineer (proxy: Software Developers)": "15-1252.00",
};

// Model parameters (edit to match your salary guide assumptions)
const INFLATION_RATE = 0.03;
const DEMAND_GROWTH = 0.12;

const SKILLS_PREMIUMS = {
  SQL: 0.04,
  Python: 0.05,
  "PowerBI/Tableau": 0.04,
  "Cloud (AWS/Azure/GCP)": 0.05,
  ML: 0.06,
};

// ------------------
// Salary guide pipeline
// ------------------
function inflationAdjustment(salary) {
  return salary * (1 + INFLATION_RATE);
}

function demandGrowthAdjustment(salary, roleBonus = 0) {
  return salary * (1 + DEMAND_GROWTH + roleBonus);
}

function skillsPremiumAdjustment(salary) {
  const total = Object.values(SKILLS_PREMIUMS).reduce((a, b) => a + b, 0);
  return salary * (1 + total);
}

// ------------------
// Robust CSV parsing (handles quoted values)
// ------------------
function parseCsvLine(line) {
  const out = [];
  let cur = "";
  let inQuotes = false;

  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        cur += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (ch === "," && !inQuotes) {
      out.push(cur);
      cur = "";
    } else {
      cur += ch;
    }
  }
  out.push(cur);
  return out;
}

function moneyToNumber(s) {
  return Number(String(s).replace(/\$/g, "").replace(/,/g, "").trim());
}

async function fetchOnetPercentilesFromCsv(onetCode) {
  const csvUrl = ONET_CSV_URL(onetCode);
  const pageUrl = ONET_PAGE_URL(onetCode);

  const resp = await axios.get(csvUrl, {
    timeout: 30000,
    headers: {
      "User-Agent":
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
      Accept: "text/csv,*/*",
    },
    validateStatus: (s) => s >= 200 && s < 300,
  });

  const csvText = resp.data;
  const lines = csvText
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);

  if (lines.length < 2) {
    throw new Error(`CSV looks empty for ${onetCode} (${csvUrl})`);
  }

  const header = parseCsvLine(lines[0]).map((h) => h.trim());
  const idxLoc = header.findIndex((h) => h.toLowerCase() === "location");
  const idxP10 = header.findIndex((h) => h.toLowerCase().includes("annual low"));
  const idxP50 = header.findIndex((h) => h.toLowerCase().includes("annual median"));
  const idxP90 = header.findIndex((h) => h.toLowerCase().includes("annual high"));

  if ([idxLoc, idxP10, idxP50, idxP90].some((i) => i < 0)) {
    throw new Error(
      `CSV header not as expected for ${onetCode}. Header=${JSON.stringify(header)}`
    );
  }

  let usRow = null;
  for (let i = 1; i < lines.length; i++) {
    const cols = parseCsvLine(lines[i]);
    if ((cols[idxLoc] || "").trim() === "United States") {
      usRow = cols;
      break;
    }
  }

  if (!usRow) {
    throw new Error(`Could not find "United States" row in CSV for ${onetCode}`);
  }

  const p10 = moneyToNumber(usRow[idxP10]);
  const p50 = moneyToNumber(usRow[idxP50]);
  const p90 = moneyToNumber(usRow[idxP90]);

  if (![p10, p50, p90].every((v) => Number.isFinite(v) && v > 0)) {
    throw new Error(
      `Parsed wages invalid for ${onetCode}. p10=${p10}, p50=${p50}, p90=${p90}`
    );
  }

  return { sourceUrl: pageUrl, csvUrl, p10, p50, p90 };
}

// ------------------
// CSV + HTML Reports (kept from your script)
// ------------------
function generateCSVReport(baseRows, predicted) {
  let csv =
    "Role,O*NET Code,Source Page,Source CSV,Entry Base (p10),Mid Base (p50),Senior Base (p90),Entry 2026,Mid 2026,Senior 2026\n";

  predicted.forEach((row) => {
    const baseRow = baseRows.find((b) => b.role === row.role);
    csv += `"${row.role}",${baseRow.code},"${baseRow.sourceUrl}","${baseRow.csvUrl}",${baseRow.baseEntry},${baseRow.baseMid},${baseRow.baseSenior},${row.entry.toFixed(
      2
    )},${row.mid.toFixed(2)},${row.senior.toFixed(2)}\n`;
  });

  fs.writeFileSync("salary_data.csv", csv, "utf-8");
  console.log("📊 Données CSV sauvegardées: salary_data.csv");
}

function generateHTMLReport(baseRows, predicted) {
  const skillsSum = Object.values(SKILLS_PREMIUMS).reduce((a, b) => a + b, 0);

  const html = `<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>Data Analytics Salary Guide 2026</title>
  <style>
    body{font-family:Arial,sans-serif;margin:40px;background:#f5f5f5;}
    .container{max-width:1100px;margin:0 auto;background:#fff;padding:30px;border-radius:10px;box-shadow:0 2px 10px rgba(0,0,0,.1);}
    h1{color:#2c3e50;border-bottom:2px solid #3498db;padding-bottom:10px;}
    table{width:100%;border-collapse:collapse;margin:16px 0;}
    th,td{padding:10px;text-align:left;border-bottom:1px solid #ddd;}
    th{background:#3498db;color:#fff;}
    tr:hover{background:#f7f7f7;}
    .note{background:#f8f9fa;padding:14px;border-radius:6px;margin:16px 0;}
    .currency{font-family:monospace;font-weight:700;color:#27ae60;}
    .footer{margin-top:26px;padding-top:14px;border-top:1px solid #ddd;color:#666;font-size:13px;}
  </style>
</head>
<body>
<div class="container">
  <h1>📊 Data Analytics Salary Guide 2026 (O*NET / BLS)</h1>

  <div class="note">
    <b>Sources “légitimes” utilisées:</b>
    <ul>
      <li>O*NET OnLine “National Wages” — téléchargement CSV “Save Table: CSV”.</li>
      <li>Percentiles p10/p50/p90.</li>
    </ul>
    <b>Paramètres du modèle:</b>
    Inflation ${(INFLATION_RATE * 100).toFixed(2)}% • Demande ${(DEMAND_GROWTH * 100).toFixed(2)}% • Compétences +${(skillsSum * 100).toFixed(2)}%
  </div>

  <h2>Salaire de base (O*NET/BLS)</h2>
  <table>
    <thead><tr>
      <th>Rôle</th><th>Code O*NET</th><th>Entry (p10)</th><th>Mid (p50)</th><th>Senior (p90)</th><th>Sources</th>
    </tr></thead>
    <tbody>
      ${baseRows
        .map(
          (r) => `<tr>
        <td>${r.role}</td>
        <td>${r.code}</td>
        <td class="currency">$${r.baseEntry.toLocaleString()}</td>
        <td class="currency">$${r.baseMid.toLocaleString()}</td>
        <td class="currency">$${r.baseSenior.toLocaleString()}</td>
        <td>
          <a href="${r.sourceUrl}" target="_blank">Page O*NET</a> ·
          <a href="${r.csvUrl}" target="_blank">CSV O*NET</a>
        </td>
      </tr>`
        )
        .join("")}
    </tbody>
  </table>

  <h2>Prédictions 2026 (inflation + demande + compétences)</h2>
  <table>
    <thead><tr>
      <th>Rôle</th><th>Entry 2026</th><th>Mid 2026</th><th>Senior 2026</th><th>Δ Mid</th>
    </tr></thead>
    <tbody>
      ${predicted
        .map((p) => {
          const base = baseRows.find((b) => b.role === p.role);
          const inc = ((p.mid / base.baseMid - 1) * 100).toFixed(1);
          return `<tr>
            <td>${p.role}</td>
            <td class="currency">$${p.entry.toLocaleString(undefined,{maximumFractionDigits:0})}</td>
            <td class="currency">$${p.mid.toLocaleString(undefined,{maximumFractionDigits:0})}</td>
            <td class="currency">$${p.senior.toLocaleString(undefined,{maximumFractionDigits:0})}</td>
            <td>+${inc}%</td>
          </tr>`;
        })
        .join("")}
    </tbody>
  </table>

  <div class="note">
    ✅ Un graphique PNG est aussi généré: <b>salary_chart.png</b>
  </div>

  <div class="footer">
    Généré le: ${new Date().toLocaleString("fr-FR")}
  </div>
</div>
</body>
</html>`;

  fs.writeFileSync("salary_report.html", html, "utf-8");
  console.log("📄 Rapport HTML généré: salary_report.html");
}

// ------------------
// NEW: Generate PNG chart with Chart.js
// ------------------
async function generatePNGChart(baseRows, predicted) {
  const width = 1400;
  const height = 650;
  const chartJSNodeCanvas = new ChartJSNodeCanvas({ width, height });

  const labels = predicted.map((r) => r.role.split("(")[0].trim());
  const entry = predicted.map((r) => Math.round(r.entry));
  const mid = predicted.map((r) => Math.round(r.mid));
  const senior = predicted.map((r) => Math.round(r.senior));

  const config = {
    type: "bar",
    data: {
      labels,
      datasets: [
        { label: "Entry (p10 proxy)", data: entry },
        { label: "Mid (p50 proxy)", data: mid },
        { label: "Senior (p90 proxy)", data: senior },
      ],
    },
    options: {
      responsive: false,
      plugins: {
        title: {
          display: true,
          text: "Data Analytics Salaries (2026) — O*NET/BLS Base + Adjustments",
        },
        legend: { display: true },
      },
      scales: {
        y: {
          title: { display: true, text: "Salary (USD)" },
        },
        x: {
          title: { display: true, text: "Role" },
        },
      },
    },
  };

  const image = await chartJSNodeCanvas.renderToBuffer(config);
  fs.writeFileSync("salary_chart.png", image);
  console.log("🖼️  Chart PNG generated: salary_chart.png");

  // Optional: also save the plotted values as JSON for traceability
  fs.writeFileSync(
    "salary_chart_data.json",
    JSON.stringify({ baseRows, predicted }, null, 2),
    "utf-8"
  );
  console.log("🧾 Chart data saved: salary_chart_data.json");
}

// ------------------
// Main
// ------------------
async function main() {
  console.log("🚀 Data Analytics Salary Guide 2026 (LEGIT-ONLY + PNG CHART)");
  console.log("=".repeat(70));

  const baseRows = [];
  const errors = [];

  console.log("\n=== DATA SOURCES USED (LIVE) ===");
  for (const [role, code] of Object.entries(ROLE_TO_ONET)) {
    const pageUrl = ONET_PAGE_URL(code);
    const csvUrl = ONET_CSV_URL(code);

    console.log(`📡 ${role} -> ${code}`);
    console.log(`   Page: ${pageUrl}`);
    console.log(`   CSV : ${csvUrl}`);

    try {
      const w = await fetchOnetPercentilesFromCsv(code);
      baseRows.push({
        role,
        code,
        sourceUrl: w.sourceUrl,
        csvUrl: w.csvUrl,
        baseEntry: w.p10,
        baseMid: w.p50,
        baseSenior: w.p90,
      });
      console.log(`   ✓ OK p10=${w.p10} p50=${w.p50} p90=${w.p90}`);
    } catch (e) {
      errors.push({ role, code, error: e.message, pageUrl, csvUrl });
      console.log(`   ❌ FAILED: ${e.message}`);
      fs.writeFileSync(
        `debug_${code.replaceAll(".", "_")}.json`,
        JSON.stringify({ role, code, pageUrl, csvUrl, error: e.message }, null, 2),
        "utf-8"
      );
    }
  }

  if (baseRows.length === 0) {
    console.error("\nNo roles could be fetched/parsed from O*NET. Check debug_*.json files.");
    process.exit(1);
  }

  // Predictions (inflation -> demand -> skills)
  const predicted = baseRows.map((r) => {
    let entry = inflationAdjustment(r.baseEntry);
    let mid = inflationAdjustment(r.baseMid);
    let senior = inflationAdjustment(r.baseSenior);

    entry = demandGrowthAdjustment(entry);
    mid = demandGrowthAdjustment(mid);
    senior = demandGrowthAdjustment(senior);

    entry = skillsPremiumAdjustment(entry);
    mid = skillsPremiumAdjustment(mid);
    senior = skillsPremiumAdjustment(senior);

    return { role: r.role, entry, mid, senior };
  });

  console.log("\n=== BASE WAGES (O*NET/BLS-backed) ===");
  baseRows.forEach((r) => {
    console.log(
      `${r.role}\n  p10=$${r.baseEntry.toLocaleString()}  p50=$${r.baseMid.toLocaleString()}  p90=$${r.baseSenior.toLocaleString()}\n  Page: ${r.sourceUrl}\n  CSV : ${r.csvUrl}\n`
    );
  });

  console.log("=== MODEL PARAMETERS ===");
  console.log(`Inflation: ${(INFLATION_RATE * 100).toFixed(2)}%`);
  console.log(`Demand:    ${(DEMAND_GROWTH * 100).toFixed(2)}%`);
  console.log(
    `Skills sum: ${(
      Object.values(SKILLS_PREMIUMS).reduce((a, b) => a + b, 0) * 100
    ).toFixed(2)}%`
  );

  console.log("\n=== PREDICTED SALARIES (2026) ===");
  predicted.forEach((r) => {
    console.log(
      `${r.role}\n  entry=$${Math.round(r.entry).toLocaleString()} mid=$${Math.round(
        r.mid
      ).toLocaleString()} senior=$${Math.round(r.senior).toLocaleString()}\n`
    );
  });

  // Reports
  generateHTMLReport(baseRows, predicted);
  generateCSVReport(baseRows, predicted);

  // NEW: PNG chart
  await generatePNGChart(baseRows, predicted);

  if (errors.length) {
    fs.writeFileSync("errors.json", JSON.stringify(errors, null, 2), "utf-8");
    console.log(`⚠️  Some roles failed. See errors.json and debug_*.json`);
  }

  console.log("✅ Done. Files generated:");
  console.log(" - salary_report.html");
  console.log(" - salary_data.csv");
  console.log(" - salary_chart.png");
  console.log(" - salary_chart_data.json");
}

main().catch((err) => {
  console.error("❌ Fatal error:", err.message);
  process.exit(1);
});
