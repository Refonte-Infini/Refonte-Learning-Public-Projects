const fs = require("fs");

const OUT_JSON = "salary_guide_2026.json";
const OUT_BAR = "chart_2026_mid_by_role.svg";
const OUT_RANGE = "chart_2026_low_high_by_role.svg";

const CFG = {
  baseYear: 2025,
  targetYear: 2026,
  inflationRate: 0.03,
  cagrByLevel: { Entry: 0.06, Mid: 0.07, Senior: 0.08, Lead: 0.085, Manager: 0.085, Director: 0.09 },
  skillPremium: {
    "Python": 0.03, "SQL": 0.02, "Tableau": 0.02, "Power BI": 0.02, "Looker": 0.02,
    "dbt": 0.03, "Snowflake": 0.03, "BigQuery": 0.03, "Spark": 0.03,
    "Machine Learning": 0.04, "Experimentation": 0.03, "DAX": 0.02, "Excel": 0.01, "Leadership": 0.015
  },
  locationMultiplier: { US: 1.00, EU: 0.85, UK: 0.90, CA: 0.90, Remote: 0.95, MEA: 0.65, APAC: 0.80 }
};

const BASELINES_2025 = [
  { role: "Data Analyst",              level: "Mid",     base_low: 65000,  base_high: 95000,  skills: ["SQL","Excel","Power BI"],          location: "US", demand_index: 1.10 },
  { role: "BI Analyst",                level: "Mid",     base_low: 70000,  base_high: 105000, skills: ["SQL","Power BI","DAX"],            location: "US", demand_index: 1.08 },
  { role: "Business Analyst",          level: "Mid",     base_low: 70000,  base_high: 110000, skills: ["SQL","Excel"],                     location: "US", demand_index: 1.06 },
  { role: "Product Analyst",           level: "Mid",     base_low: 80000,  base_high: 125000, skills: ["SQL","Experimentation"],            location: "US", demand_index: 1.12 },
  { role: "Analytics Engineer",        level: "Senior",  base_low: 110000, base_high: 165000, skills: ["SQL","Python","dbt","Snowflake"],   location: "US", demand_index: 1.18 },
  { role: "Data Engineer",             level: "Senior",  base_low: 115000, base_high: 175000, skills: ["SQL","Python","Spark"],             location: "US", demand_index: 1.17 },
  { role: "Data Scientist",            level: "Senior",  base_low: 120000, base_high: 180000, skills: ["Python","Machine Learning"],         location: "US", demand_index: 1.15 },
  { role: "Machine Learning Engineer", level: "Senior",  base_low: 140000, base_high: 210000, skills: ["Python","Machine Learning"],         location: "US", demand_index: 1.20 },
  { role: "BI Developer",              level: "Senior",  base_low: 90000,  base_high: 140000, skills: ["SQL","Power BI"],                   location: "US", demand_index: 1.10 },
  { role: "Marketing Analyst",         level: "Mid",     base_low: 65000,  base_high: 100000, skills: ["SQL","Excel"],                      location: "US", demand_index: 1.07 },
  { role: "AI Analyst",                level: "Mid",     base_low: 85000,  base_high: 135000, skills: ["SQL","Python"],                     location: "US", demand_index: 1.13 },
  { role: "Analytics Manager",         level: "Manager", base_low: 130000, base_high: 200000, skills: ["SQL","Leadership"],                 location: "US", demand_index: 1.12 },
];

function yearsBetween(baseYear, targetYear) { return Math.max(0, targetYear - baseYear); }
function applyCagr(amount, cagr, years) { return amount * Math.pow(1 + cagr, years); }
function applyInflation(amount, infl, years) { return amount * Math.pow(1 + infl, years); }
function skillsMultiplier(skills, premium) {
  return skills.reduce((m, s) => m * (1 + (premium[s] || 0)), 1.0);
}

function predict(r) {
  const yrs = yearsBetween(CFG.baseYear, CFG.targetYear);
  const cagr = CFG.cagrByLevel[r.level] ?? 0.06;

  let low = applyCagr(r.base_low, cagr, yrs);
  let high = applyCagr(r.base_high, cagr, yrs);

  low = applyInflation(low, CFG.inflationRate, yrs);
  high = applyInflation(high, CFG.inflationRate, yrs);

  const sm = skillsMultiplier(r.skills, CFG.skillPremium);
  low *= sm; high *= sm;

  const lm = CFG.locationMultiplier[r.location] ?? 0.90;
  low *= lm * r.demand_index;
  high *= lm * r.demand_index;

  const mid = (low + high) / 2.0;
  return { low, high, mid };
}

// Minimal SVG helpers (no deps)
function svgHeader(w, h) {
  return `<?xml version="1.0" encoding="UTF-8"?>\n<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">\n`;
}
function svgFooter() { return `</svg>\n`; }
function esc(s){ return String(s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;"); }

function makeBarChart(roles, values, title, outFile) {
  const W = 1400, H = 700;
  const padL = 80, padR = 30, padT = 60, padB = 220;
  const chartW = W - padL - padR;
  const chartH = H - padT - padB;

  const maxV = Math.max(...values) * 1.1;
  const barCount = values.length;
  const gap = 10;
  const barW = Math.max(10, (chartW - gap * (barCount - 1)) / barCount);

  let svg = svgHeader(W, H);
  svg += `<rect x="0" y="0" width="${W}" height="${H}" fill="white"/>\n`;
  svg += `<text x="${W/2}" y="35" font-size="22" text-anchor="middle" font-family="Arial">${esc(title)}</text>\n`;

  // Axes
  svg += `<line x1="${padL}" y1="${padT}" x2="${padL}" y2="${padT+chartH}" stroke="black"/>\n`;
  svg += `<line x1="${padL}" y1="${padT+chartH}" x2="${padL+chartW}" y2="${padT+chartH}" stroke="black"/>\n`;

  for (let i = 0; i < barCount; i++) {
    const v = values[i];
    const h = (v / maxV) * chartH;
    const x = padL + i * (barW + gap);
    const y = padT + (chartH - h);
    svg += `<rect x="${x}" y="${y}" width="${barW}" height="${h}" fill="#4c78a8"/>\n`;
    svg += `<text x="${x + barW/2}" y="${padT+chartH+18}" font-size="12" text-anchor="end" transform="rotate(-45 ${x + barW/2} ${padT+chartH+18})" font-family="Arial">${esc(roles[i])}</text>\n`;
  }

  svg += svgFooter();
  fs.writeFileSync(outFile, svg, "utf-8");
}

function makeRangeChart(roles, lows, highs, title, outFile) {
  const W = 1400, H = 700;
  const padL = 80, padR = 30, padT = 60, padB = 220;
  const chartW = W - padL - padR;
  const chartH = H - padT - padB;

  const maxV = Math.max(...highs) * 1.1;
  const n = roles.length;
  const gap = 10;
  const bandW = Math.max(10, (chartW - gap * (n - 1)) / n);

  function yScale(v){ return padT + (chartH - (v / maxV) * chartH); }

  let svg = svgHeader(W, H);
  svg += `<rect x="0" y="0" width="${W}" height="${H}" fill="white"/>\n`;
  svg += `<text x="${W/2}" y="35" font-size="22" text-anchor="middle" font-family="Arial">${esc(title)}</text>\n`;

  // Axes
  svg += `<line x1="${padL}" y1="${padT}" x2="${padL}" y2="${padT+chartH}" stroke="black"/>\n`;
  svg += `<line x1="${padL}" y1="${padT+chartH}" x2="${padL+chartW}" y2="${padT+chartH}" stroke="black"/>\n`;

  for (let i = 0; i < n; i++) {
    const x = padL + i * (bandW + gap) + bandW/2;
    const yLow = yScale(lows[i]);
    const yHigh = yScale(highs[i]);

    // vertical range line
    svg += `<line x1="${x}" y1="${yHigh}" x2="${x}" y2="${yLow}" stroke="#f58518" stroke-width="4"/>\n`;
    // caps
    svg += `<line x1="${x-10}" y1="${yHigh}" x2="${x+10}" y2="${yHigh}" stroke="#f58518" stroke-width="4"/>\n`;
    svg += `<line x1="${x-10}" y1="${yLow}" x2="${x+10}" y2="${yLow}" stroke="#f58518" stroke-width="4"/>\n`;

    // label
    svg += `<text x="${x}" y="${padT+chartH+18}" font-size="12" text-anchor="end" transform="rotate(-45 ${x} ${padT+chartH+18})" font-family="Arial">${esc(roles[i])}</text>\n`;
  }

  svg += svgFooter();
  fs.writeFileSync(outFile, svg, "utf-8");
}

function main() {
  const out = BASELINES_2025.map(r => {
    const p = predict(r);
    return {
      role: r.role,
      level: r.level,
      base_year: CFG.baseYear,
      target_year: CFG.targetYear,
      location: r.location,
      skills: r.skills,
      demand_index: r.demand_index,
      base_low: r.base_low,
      base_high: r.base_high,
      projected_low: Number(p.low.toFixed(2)),
      projected_high: Number(p.high.toFixed(2)),
      projected_mid: Number(p.mid.toFixed(2)),
    };
  });

  fs.writeFileSync(OUT_JSON, JSON.stringify(out, null, 2), "utf-8");

  const roles = out.map(x => x.role);
  const mids = out.map(x => x.projected_mid);
  const lows = out.map(x => x.projected_low);
  const highs = out.map(x => x.projected_high);

  makeBarChart(roles, mids, "Projected 2026 Mid Salary by Role", OUT_BAR);
  makeRangeChart(roles, lows, highs, "Projected 2026 Salary Range (Low–High) by Role", OUT_RANGE);

  console.log(`✅ Wrote ${OUT_JSON}`);
  console.log(`✅ Saved ${OUT_BAR}`);
  console.log(`✅ Saved ${OUT_RANGE}`);
}

main();
