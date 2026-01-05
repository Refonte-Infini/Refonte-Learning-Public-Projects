// Cybersecurity Salary Guide Prediction (US, 2026) - JavaScript reference implementation

const BASELINES_2024 = {
  cybersecurity_engineer: { entry: [90000, 120000], mid: [120000, 160000], senior: [160000, 210000] },
  cloud_security_engineer: { entry: [105000, 135000], mid: [135000, 175000], senior: [175000, 230000] },
  devsecops_engineer: { entry: [100000, 130000], mid: [130000, 170000], senior: [170000, 225000] },
  appsec_engineer: { entry: [100000, 135000], mid: [135000, 180000], senior: [180000, 240000] },
  soc_analyst: { entry: [70000, 95000], mid: [95000, 125000], senior: [125000, 160000] },
  incident_response_dfir: { entry: [95000, 125000], mid: [125000, 165000], senior: [165000, 220000] },
  threat_hunter: { entry: [100000, 130000], mid: [130000, 175000], senior: [175000, 230000] },
  penetration_tester: { entry: [85000, 115000], mid: [115000, 155000], senior: [155000, 210000] },
  iam_engineer: { entry: [90000, 120000], mid: [120000, 160000], senior: [160000, 210000] },
  security_architect: { entry: [135000, 175000], mid: [175000, 220000], senior: [220000, 280000] },
  grc_analyst: { entry: [80000, 105000], mid: [105000, 140000], senior: [140000, 185000] },
};

const CAGR_BY_ROLE = {
  default: 0.05,
  cloud_security_engineer: 0.07,
  devsecops_engineer: 0.07,
  appsec_engineer: 0.06,
  security_architect: 0.06,
};

const INFLATION_2024_TO_2026 = 1.07;

const GEO_MULTIPLIER = {
  CA: 1.18, NY: 1.15, WA: 1.12, MA: 1.10, DC: 1.12, VA: 1.07,
  TX: 1.03, FL: 1.00, IL: 1.02, CO: 1.05, GA: 0.98, NC: 0.98,
  AZ: 0.97, OH: 0.95, PA: 0.97, REMOTE: 1.00, DEFAULT: 1.00,
};

const SKILL_PREMIUM = {
  aws_security: 0.05, azure_security: 0.05, gcp_security: 0.05,
  kubernetes: 0.04, terraform: 0.03, containers: 0.03, cnapp: 0.04, cspm: 0.03,
  zero_trust: 0.04, iam: 0.03, okta: 0.02, entra_id: 0.02, sso_saml_oidc: 0.02,
  siem: 0.03, soar: 0.03, edr: 0.02,
  dfir: 0.05, incident_response: 0.04, threat_hunting: 0.04, malware_analysis: 0.04, reverse_engineering: 0.04,
  secure_sdlc: 0.03, sast_dast: 0.03, threat_modeling: 0.03,
  oscp: 0.06, gcih: 0.05, gcfa: 0.05, gpen: 0.05, cissp: 0.05, ccsp: 0.05, security_plus: 0.02,
};

const ROLE_ALIASES = new Map([
  ["security engineer", "cybersecurity_engineer"],
  ["cybersecurity engineer", "cybersecurity_engineer"],
  ["cloud security engineer", "cloud_security_engineer"],
  ["devsecops engineer", "devsecops_engineer"],
  ["application security engineer", "appsec_engineer"],
  ["appsec engineer", "appsec_engineer"],
  ["soc analyst", "soc_analyst"],
  ["incident response", "incident_response_dfir"],
  ["dfir", "incident_response_dfir"],
  ["threat hunter", "threat_hunter"],
  ["penetration tester", "penetration_tester"],
  ["pen tester", "penetration_tester"],
  ["red team", "penetration_tester"],
  ["iam engineer", "iam_engineer"],
  ["security architect", "security_architect"],
  ["grc analyst", "grc_analyst"],
]);

const LEVEL_ALIASES = new Map([
  ["junior", "entry"], ["entry", "entry"], ["entry-level", "entry"],
  ["mid", "mid"], ["mid-level", "mid"], ["intermediate", "mid"],
  ["senior", "senior"], ["lead", "senior"], ["staff", "senior"], ["principal", "senior"],
]);

function normalizeRole(role) {
  const r = (role || "").trim().toLowerCase();
  if (ROLE_ALIASES.has(r)) return ROLE_ALIASES.get(r);
  for (const [k, v] of ROLE_ALIASES.entries()) {
    if (r.includes(k)) return v;
  }
  const keyish = r.replace(/\s+/g, "_");
  if (BASELINES_2024[keyish]) return keyish;
  return "cybersecurity_engineer";
}

function normalizeLevel(level) {
  const l = (level || "").trim().toLowerCase();
  if (LEVEL_ALIASES.has(l)) return LEVEL_ALIASES.get(l);
  for (const [k, v] of LEVEL_ALIASES.entries()) {
    if (l.includes(k)) return v;
  }
  return "mid";
}

function applyCagr(low, high, roleKey, years = 2) {
  const cagr = CAGR_BY_ROLE[roleKey] ?? CAGR_BY_ROLE.default;
  const factor = Math.pow(1 + cagr, years);
  return [low * factor, high * factor];
}

function applyInflation(low, high) {
  return [low * INFLATION_2024_TO_2026, high * INFLATION_2024_TO_2026];
}

function skillsMultiplier(skills) {
  let total = 0;
  for (const s of skills || []) total += (SKILL_PREMIUM[s] ?? 0);
  total = Math.min(total, 0.25);
  return 1 + (0.85 * total);
}

function geoMultiplier(state) {
  const key = (state || "").trim().toUpperCase();
  return GEO_MULTIPLIER[key] ?? GEO_MULTIPLIER.DEFAULT;
}

function regressionAdjust(roleKey, level, yearsExp) {
  const target = { entry: 1.0, mid: 4.0, senior: 8.0 }[level] ?? 4.0;
  const delta = (yearsExp ?? 0) - target;
  let leverage = 0.008;
  if (["security_architect", "cloud_security_engineer", "devsecops_engineer"].includes(roleKey)) leverage = 0.010;
  const adj = delta * leverage;
  const clipped = Math.max(-0.05, Math.min(0.08, adj));
  return 1 + clipped;
}

function predict2026({ role, level, yearsExperience, state, skills }) {
  const roleKey = normalizeRole(role);
  const lvl = normalizeLevel(level);
  const base = BASELINES_2024[roleKey]?.[lvl] ?? BASELINES_2024.cybersecurity_engineer.mid;
  const [baseLow, baseHigh] = base;

  const [cagrLow, cagrHigh] = applyCagr(baseLow, baseHigh, roleKey);
  const [inflLow, inflHigh] = applyInflation(cagrLow, cagrHigh);

  const sMult = skillsMultiplier(skills);
  const gMult = geoMultiplier(state);
  const rMult = regressionAdjust(roleKey, lvl, yearsExperience);

  const aLow = inflLow, aHigh = inflHigh;
  const bLow = inflLow * sMult, bHigh = inflHigh * sMult;
  const dLow = inflLow * sMult * gMult, dHigh = inflHigh * sMult * gMult;
  const eLow = dLow * rMult, eHigh = dHigh * rMult;

  // Blend A->B->D->E
  const wSkills = 0.20, wGeo = 0.20, wReg = 0.20;

  let low = (1 - wSkills) * aLow + wSkills * bLow;
  let high = (1 - wSkills) * aHigh + wSkills * bHigh;

  low = (1 - wGeo) * low + wGeo * dLow;
  high = (1 - wGeo) * high + wGeo * dHigh;

  low = (1 - wReg) * low + wReg * eLow;
  high = (1 - wReg) * high + wReg * eHigh;

  const mid = (low + high) / 2;

  return {
    roleKey,
    level: lvl,
    finalLow: low,
    finalMid: mid,
    finalHigh: high,
    skillsMultiplier: sMult,
    geoMultiplier: gMult,
    regressionAdjustment: rMult,
  };
}

function money(x) {
  return `$${Math.round(x).toLocaleString("en-US")}`;
}

function demo() {
  const examples = [
    {
      role: "Cybersecurity Engineer",
      level: "Mid",
      yearsExperience: 4,
      state: "TX",
      skills: ["siem", "soar", "zero_trust", "cissp"],
    },
    {
      role: "Cloud Security Engineer",
      level: "Senior",
      yearsExperience: 9,
      state: "CA",
      skills: ["aws_security", "kubernetes", "terraform", "cnapp", "ccsp"],
    },
    {
      role: "SOC Analyst",
      level: "Entry",
      yearsExperience: 1,
      state: "REMOTE",
      skills: ["siem", "edr", "security_plus"],
    },
  ];

  for (const ex of examples) {
    const out = predict2026(ex);
    console.log("=".repeat(72));
    console.log(`${ex.role} (${ex.level}) - ${ex.state}, ${ex.yearsExperience} yrs`);
    console.log(`Predicted 2026: ${money(out.finalLow)} – ${money(out.finalHigh)} (mid ${money(out.finalMid)})`);
    console.log(`Multipliers: skills=${out.skillsMultiplier.toFixed(3)} geo=${out.geoMultiplier.toFixed(3)} reg=${out.regressionAdjustment.toFixed(3)}`);
  }
}

demo();
