/**
 * Software Engineering Salary Prediction (LEGIT SOURCES) + CHART (PNG)
 * -------------------------------------------------------------------
 * This is the SAME style/pipeline as your JS example:
 * - fetch data
 * - optional BERT (off by default)
 * - apply inflation + skills premium + demand + geographic
 * - print results
 * - generate a chart
 *
 * ✅ Legit data source:
 * We fetch wage percentiles from O*NET OnLine "National Wages" pages.
 * Those pages explicitly cite Bureau of Labor Statistics (BLS) wage data.
 *
 * We use annual wage percentiles as level proxies:
 * - Entry  = 10th percentile (p10)
 * - Mid    = 50th percentile (median, p50)
 * - Senior = 90th percentile (p90)
 *
 * Output chart file:
 *   software_engineering_bls_onet_2026.png
 *
 * ---------------------------------------------------------------
 * INSTALL:
 *   npm i node-fetch cheerio chartjs-node-canvas chart.js @huggingface/transformers
 *
 * RUN:
 *   node main.mjs
 *   node main.mjs --year 2026 --inflation 0.03 --leadership false
 */

import fetch from "node-fetch";
import cheerio from "cheerio";
import { ChartJSNodeCanvas } from "chartjs-node-canvas";

// Optional (kept for parity with your sample; OFF by default)
import { pipeline } from "@huggingface/transformers";

// ============================================================
// 1) "WHERE THE DATA CAME FROM" (LEGIT SOURCE MAPPING)
// ============================================================
// O*NET occupation codes -> O*NET national wages page:
//   https://www.onetonline.org/link/localwages/{code}
//
// NOTE: Government sources don't publish "DevOps Engineer" as a separate occupation,
// so we use transparent proxies to official occupations and print them.
const ROLE_TO_ONET = {
  "Software Developer": "15-1252.00",
  "Web Developer": "15-1254.00",
  "Information Security Analyst": "15-1212.00",
  "Data Scientist (proxy for ML Engineer)": "15-2051.00",

  // proxies (clearly labeled):
  "DevOps Engineer (proxy: Software Developers)": "15-1252.00",
  "Cloud Engineer (proxy: Network & Computer Systems Admins)": "15-1244.00",
};

const ONET_WAGES_URL = (code) => `https://www.onetonline.org/link/localwages/${code}`;

// ============================================================
// 2) Demand / Geographic / Skills factors (replace with your real ones)
// ============================================================
const DEMAND_FACTOR = {
  "Software Developer": 0.10,
  "Web Developer": 0.08,
  "Information Security Analyst": 0.13,
  "Data Scientist (proxy for ML Engineer)": 0.12,
  "DevOps Engineer (proxy: Software Developers)": 0.12,
  "Cloud Engineer (proxy: Network & Computer Systems Admins)": 0.11,
};

const GEOGRAPHIC_FACTOR = {
  "Software Developer": 0.05,
  "Web Developer": 0.05,
  "Information Security Analyst": 0.06,
  "Data Scientist (proxy for ML Engineer)": 0.06,
  "DevOps Engineer (proxy: Software Developers)": 0.06,
  "Cloud Engineer (proxy: Network & Computer Systems Admins)": 0.05,
};

const SKILLS_PREMIUMS = {
  AWS: 0.04,
  Kubernetes: 0.05,
  Terraform: 0.03,
  Security: 0.03,
  SystemDesign: 0.04,
  MachineLearning: 0.06,
};

// ============================================================
// 3) Utility functions for fetching + parsing O*NET wages
// ============================================================
const moneyToNumber = (s) => Number(String(s).replace(/\$/g, "").replace(/,/g, "").trim());

async function fetchOnetPercentileWages(onetCode) {
  const url = ONET_WAGES_URL(onetCode);
  const res = await fetch(url, { timeout: 30000 });
  if (!res.ok) throw new Error(`Failed to fetch ${url} (${res.status})`);

  const html = await res.text();
  const $ = cheerio.load(html);
  const text = $("body").text().replace(/\s+/g, " ").trim();

  // Find the "United States" row in text form and grab 5 dollar amounts:
  // Annual Low (10%), Annual Q L (25%), Annual Median (50%), Annual Q U (75%), Annual High (90%)
  const match = text.match(/United States\s+\$[\d,]+\s+\$[\d,]+\s+\$[\d,]+\s+\$[\d,]+\s+\$[\d,]+/);
  if (!match) {
    throw new Error(`Could not locate United States wage row for ${onetCode} at ${url}`);
  }

  const dollars = match[0].match(/\$[\d,]+/g) || [];
  if (dollars.length < 5) {
    throw new Error(`Unexpected wage row format for ${onetCode}: ${match[0]}`);
  }

  const p10 = moneyToNumber(dollars[0]);
  const p50 = moneyToNumber(dollars[2]);
  const p90 = moneyToNumber(dollars[4]);

  return { sourceUrl: url, p10, p50, p90 };
}

// ============================================================
// 4) Models (same style as your sample)
// ============================================================
const calculateCAGR = (presentValue, futureValue, years) => {
  return Math.pow(futureValue / presentValue, 1 / years) - 1;
};

const adjustForInflation = (salary, inflationRate) => salary * (1 + inflationRate);

const adjustForSkillsPremium = (baseSalary, skillFactors) => {
  const totalPremium = Object.values(skillFactors).reduce((a, b) => a + b, 0);
  return baseSalary * (1 + totalPremium);
};

const adjustForDemandAndGeography = (baseSalary, demandFactor, geographicFactor) => {
  return baseSalary * (1 + demandFactor + geographicFactor);
};

// ============================================================
// 5) Optional BERT extraction (kept for parity; OFF by default)
// ============================================================
const initializeBERT = async () => {
  // If you actually want NER extraction, you’ll need a NER-capable model.
  // This keeps the same interface as your sample.
  return await pipeline("ner", "Xenova/bert-base-cased");
};

const extractSalaryDetails = async (nerPipeline, jobDescription) => {
  const nerResults = await nerPipeline(jobDescription);
  return nerResults; // left minimal since it's OFF by default
};

// ============================================================
// 6) Chart rendering
// ============================================================
async function renderChart(results, yearLabel, outPath) {
  const width = 1500;
  const height = 650;
  const canvas = new ChartJSNodeCanvas({ width, height, backgroundColour: "white" });

  const roles = results.map((r) => r.Role);
  const entry = results.map((r) => Number(r[`Entry-Level ${yearLabel}`]));
  const mid = results.map((r) => Number(r[`Mid-Level ${yearLabel}`]));
  const senior = results.map((r) => Number(r[`Senior-Level ${yearLabel}`]));

  const config = {
    type: "bar",
    data: {
      labels: roles,
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
          text: `Software Engineering Salaries (${yearLabel}) — O*NET (BLS wage data) + Adjustments`,
        },
        legend: { display: true },
      },
      scales: {
        x: { ticks: { maxRotation: 30, minRotation: 30 } },
        y: { title: { display: true, text: "Salary (USD)" } },
      },
    },
  };

  const buffer = await canvas.renderToBuffer(config);
  await (await import("fs")).promises.writeFile(outPath, buffer);
  console.log(`\nChart saved to: ${outPath}`);
}

// ============================================================
// 7) CLI args
// ============================================================
function parseArgs() {
  const args = process.argv.slice(2);
  const out = {};
  for (let i = 0; i < args.length; i++) {
    if (args[i].startsWith("--")) {
      const k = args[i].slice(2);
      const v = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true";
      out[k] = v;
    }
  }
  return out;
}

// ============================================================
// 8) Main
// ============================================================
async function main() {
  const a = parseArgs();

  const year = Number(a.year || 2026);
  const inflationRate = Number(a.inflation || 0.03);
  const outChart = a.out || "software_engineering_bls_onet_2026.png";

  const useBert = String(a.use_bert || "false").toLowerCase() === "true";

  // Optional BERT usage (OFF by default)
  if (useBert) {
    const ner = await initializeBERT();
    const jobDescriptions = [
      "Hiring an entry-level Software Developer. Salary range $80,000–$110,000.",
      "Hiring a senior Security Analyst. Salary up to $190,000.",
    ];
    const bertOut = [];
    for (const d of jobDescriptions) bertOut.push(await extractSalaryDetails(ner, d));
    console.log("BERT Output:");
    console.dir(bertOut, { depth: 5 });
  }

  // 1) Fetch legit wages for each role
  console.log("\n=== LEGIT SOURCES USED (O*NET National Wages pages; BLS-backed wage data) ===");
  const baseRows = [];
  for (const [role, code] of Object.entries(ROLE_TO_ONET)) {
    const w = await fetchOnetPercentileWages(code);
    console.log(`- ${role} -> O*NET ${code} -> ${w.sourceUrl}`);

    baseRows.push({
      Role: role,
      "O*NET Code": code,
      "Source URL": w.sourceUrl,
      // Base wages from legit source:
      "Entry-Level Base (p10)": w.p10,
      "Mid-Level Base (p50)": w.p50,
      "Senior-Level Base (p90)": w.p90,
    });
  }

  // Show "where data is" explicitly
  console.log("\n=== BASE WAGES PULLED (from the URLs above) ===");
  console.table(
    baseRows.map((r) => ({
      Role: r.Role,
      "O*NET Code": r["O*NET Code"],
      "p10 (Entry proxy)": r["Entry-Level Base (p10)"],
      "p50 (Mid proxy)": r["Mid-Level Base (p50)"],
      "p90 (Senior proxy)": r["Senior-Level Base (p90)"],
    }))
  );


  const results = baseRows.map((r) => {
    const role = r.Role;

    const entryBase = r["Entry-Level Base (p10)"];
    const midBase = r["Mid-Level Base (p50)"];
    const seniorBase = r["Senior-Level Base (p90)"];

    const demand = DEMAND_FACTOR[role] ?? 0;
    const geo = GEOGRAPHIC_FACTOR[role] ?? 0;

    // CAGR (synthetic, replace with real historical PV/FV if available)
    const entryCAGR = calculateCAGR(entryBase * 0.95, entryBase, 1);
    const midCAGR = calculateCAGR(midBase * 0.90, midBase, 1);
    const seniorCAGR = calculateCAGR(seniorBase * 0.85, seniorBase, 1);

    const entryInflated = adjustForInflation(entryBase, inflationRate + entryCAGR);
    const midInflated = adjustForInflation(midBase, inflationRate + midCAGR);
    const seniorInflated = adjustForInflation(seniorBase, inflationRate + seniorCAGR);

    const entrySkills = adjustForSkillsPremium(entryInflated, SKILLS_PREMIUMS);
    const midSkills = adjustForSkillsPremium(midInflated, SKILLS_PREMIUMS);
    const seniorSkills = adjustForSkillsPremium(seniorInflated, SKILLS_PREMIUMS);

    const entryFinal = adjustForDemandAndGeography(entrySkills, demand, geo);
    const midFinal = adjustForDemandAndGeography(midSkills, demand, geo);
    const seniorFinal = adjustForDemandAndGeography(seniorSkills, demand, geo);

    return {
      Role: role,
      "Source URL": r["Source URL"],
      [`Entry-Level ${year}`]: entryFinal.toFixed(2),
      [`Mid-Level ${year}`]: midFinal.toFixed(2),
      [`Senior-Level ${year}`]: seniorFinal.toFixed(2),
    };
  });

  console.log(`\n=== PREDICTED SALARIES for ${year} (after inflation + skills + demand + geo) ===`);
  console.table(results.map((r) => ({
    Role: r.Role,
    [`Entry-Level ${year}`]: r[`Entry-Level ${year}`],
    [`Mid-Level ${year}`]: r[`Mid-Level ${year}`],
    [`Senior-Level ${year}`]: r[`Senior-Level ${year}`],
  })));

  // 3) Chart
  await renderChart(results, String(year), outChart);
}

main().catch((e) => {
  console.error("Fatal error:", e);
  process.exit(1);
});
