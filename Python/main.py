from __future__ import annotations

import math
import re
from dataclasses import dataclass, asdict
from typing import Dict, List, Tuple, Optional

import numpy as np

# -----------------------------
# Config: Baselines (USD) for US market
# These are "seed" ranges you can replace with real survey data later.
# -----------------------------
BASELINES_2024: Dict[str, Dict[str, Tuple[int, int]]] = {
    # role_key: { level: (low, high) }
    "cybersecurity_engineer": {
        "entry": (90000, 120000),
        "mid": (120000, 160000),
        "senior": (160000, 210000),
    },
    "cloud_security_engineer": {
        "entry": (105000, 135000),
        "mid": (135000, 175000),
        "senior": (175000, 230000),
    },
    "devsecops_engineer": {
        "entry": (100000, 130000),
        "mid": (130000, 170000),
        "senior": (170000, 225000),
    },
    "appsec_engineer": {
        "entry": (100000, 135000),
        "mid": (135000, 180000),
        "senior": (180000, 240000),
    },
    "soc_analyst": {
        "entry": (70000, 95000),
        "mid": (95000, 125000),
        "senior": (125000, 160000),
    },
    "incident_response_dfir": {
        "entry": (95000, 125000),
        "mid": (125000, 165000),
        "senior": (165000, 220000),
    },
    "threat_hunter": {
        "entry": (100000, 130000),
        "mid": (130000, 175000),
        "senior": (175000, 230000),
    },
    "penetration_tester": {
        "entry": (85000, 115000),
        "mid": (115000, 155000),
        "senior": (155000, 210000),
    },
    "iam_engineer": {
        "entry": (90000, 120000),
        "mid": (120000, 160000),
        "senior": (160000, 210000),
    },
    "security_architect": {
        "entry": (135000, 175000),
        "mid": (175000, 220000),
        "senior": (220000, 280000),
    },
    "grc_analyst": {
        "entry": (80000, 105000),
        "mid": (105000, 140000),
        "senior": (140000, 185000),
    },
}

# CAGR assumptions from 2024 -> 2026 (2 years). Tune per role family.
CAGR_BY_ROLE: Dict[str, float] = {
    "default": 0.05,  # 5% annual
    "cloud_security_engineer": 0.07,
    "devsecops_engineer": 0.07,
    "appsec_engineer": 0.06,
    "security_architect": 0.06,
}

# Inflation multiplier (2024 dollars -> 2026 dollars). Replace with CPI-based value.
INFLATION_2024_TO_2026: float = 1.07

# Geographic multipliers (US)
GEO_MULTIPLIER: Dict[str, float] = {
    "CA": 1.18,
    "NY": 1.15,
    "WA": 1.12,
    "MA": 1.10,
    "DC": 1.12,
    "VA": 1.07,
    "TX": 1.03,
    "FL": 1.00,
    "IL": 1.02,
    "CO": 1.05,
    "GA": 0.98,
    "NC": 0.98,
    "AZ": 0.97,
    "OH": 0.95,
    "PA": 0.97,
    "REMOTE": 1.00,
    "DEFAULT": 1.00,
}

# Skills/certs premiums (additive percentage bumps)
SKILL_PREMIUM: Dict[str, float] = {
    # cloud & platform security
    "aws_security": 0.05,
    "azure_security": 0.05,
    "gcp_security": 0.05,
    "kubernetes": 0.04,
    "terraform": 0.03,
    "containers": 0.03,
    "cnapp": 0.04,
    "cspm": 0.03,

    # security engineering
    "zero_trust": 0.04,
    "iam": 0.03,
    "okta": 0.02,
    "entra_id": 0.02,
    "sso_saml_oidc": 0.02,
    "siem": 0.03,
    "soar": 0.03,
    "edr": 0.02,

    # DFIR / threat
    "dfir": 0.05,
    "incident_response": 0.04,
    "threat_hunting": 0.04,
    "malware_analysis": 0.04,
    "reverse_engineering": 0.04,

    # AppSec
    "secure_sdlc": 0.03,
    "sast_dast": 0.03,
    "threat_modeling": 0.03,

    # certs
    "oscp": 0.06,
    "gcih": 0.05,
    "gcfa": 0.05,
    "gpen": 0.05,
    "cissp": 0.05,
    "ccsp": 0.05,
    "security_plus": 0.02,
}

ROLE_ALIASES = {
    "security engineer": "cybersecurity_engineer",
    "cybersecurity engineer": "cybersecurity_engineer",
    "cyber security engineer": "cybersecurity_engineer",
    "cloud security engineer": "cloud_security_engineer",
    "devsecops engineer": "devsecops_engineer",
    "application security engineer": "appsec_engineer",
    "appsec engineer": "appsec_engineer",
    "soc analyst": "soc_analyst",
    "incident response": "incident_response_dfir",
    "dfir": "incident_response_dfir",
    "threat hunter": "threat_hunter",
    "penetration tester": "penetration_tester",
    "pen tester": "penetration_tester",
    "red team": "penetration_tester",
    "iam engineer": "iam_engineer",
    "security architect": "security_architect",
    "grc analyst": "grc_analyst",
}

LEVEL_ALIASES = {
    "junior": "entry",
    "entry": "entry",
    "entry-level": "entry",
    "mid": "mid",
    "mid-level": "mid",
    "intermediate": "mid",
    "senior": "senior",
    "lead": "senior",
    "staff": "senior",
    "principal": "senior",
}

DEFAULT_WEIGHTS = {
    "cagr": 0.20,
    "inflation": 0.20,
    "skills": 0.20,
    "geo": 0.20,
    "regression": 0.20,
}

@dataclass
class PredictionInput:
    role: str
    level: str
    years_experience: float
    state: str  # e.g., CA, NY, REMOTE
    skills: List[str]
    job_description: Optional[str] = None

@dataclass
class PredictionBreakdown:
    baseline_low: float
    baseline_high: float
    cagr_low: float
    cagr_high: float
    inflation_low: float
    inflation_high: float
    skills_multiplier: float
    geo_multiplier: float
    regression_adjustment: float
    final_low: float
    final_mid: float
    final_high: float

# -----------------------------
# Model 1: "BERT" / NLP extraction (optional)
# Falls back to regex heuristics by default to keep it runnable.
# -----------------------------
def extract_from_text(text: str) -> Dict[str, object]:
    """
    Heuristic extractor. If transformers is installed, you can wire a real NER model here.
    Returns: role, level, skills (best-effort).
    """
    t = text.lower()

    role_key = None
    for k, v in ROLE_ALIASES.items():
        if k in t:
            role_key = v
            break

    level = None
    for k, v in LEVEL_ALIASES.items():
        if re.search(rf"\b{k}\b", t):
            level = v
            break

    detected_skills = []
    for sk in SKILL_PREMIUM.keys():
        token = sk.replace("_", " ")
        if token in t or sk in t:
            detected_skills.append(sk)

    # Common explicit cert strings
    for cert in ["oscp", "cissp", "ccsp", "gcih", "gcfa", "gpen", "security+", "security plus"]:
        if cert in t:
            normalized = "security_plus" if "security" in cert else cert
            detected_skills.append(normalized)

    return {
        "role_key": role_key,
        "level": level,
        "skills": sorted(set(detected_skills)),
    }

# -----------------------------
# Model 2: CAGR
# -----------------------------
def apply_cagr(low: float, high: float, role_key: str, years: int = 2) -> Tuple[float, float]:
    cagr = CAGR_BY_ROLE.get(role_key, CAGR_BY_ROLE["default"])
    factor = (1.0 + cagr) ** years
    return low * factor, high * factor

# -----------------------------
# Model 3: Inflation Adjustment
# -----------------------------
def apply_inflation(low: float, high: float) -> Tuple[float, float]:
    return low * INFLATION_2024_TO_2026, high * INFLATION_2024_TO_2026

# -----------------------------
# Model 4: Skills Premium
# -----------------------------
def compute_skills_multiplier(skills: List[str]) -> float:
    # Diminishing returns: sum premiums but cap and dampen
    total = 0.0
    for s in skills:
        total += SKILL_PREMIUM.get(s, 0.0)
    # cap at +25% and apply mild damping
    total = min(total, 0.25)
    return 1.0 + (0.85 * total)

# -----------------------------
# Model 5: Demand & Geographic Growth
# -----------------------------
def compute_geo_multiplier(state: str) -> float:
    key = (state or "").strip().upper()
    return GEO_MULTIPLIER.get(key, GEO_MULTIPLIER["DEFAULT"])

# -----------------------------
# Model 6: Weighted Regression (lightweight surrogate)
# Instead of training, we use a calibrated adjustment from experience + role seniority.
# You can replace this with a trained sklearn model later.
# -----------------------------
def regression_adjustment(role_key: str, level: str, years_exp: float) -> float:
    # target years for levels
    target = {"entry": 1.0, "mid": 4.0, "senior": 8.0}.get(level, 4.0)
    # deviation from target -> small adjustment
    delta = years_exp - target
    # role leverage: architects and cloud tend to reward experience slightly more
    leverage = 0.008
    if role_key in {"security_architect", "cloud_security_engineer", "devsecops_engineer"}:
        leverage = 0.010
    return 1.0 + np.clip(delta * leverage, -0.05, 0.08)

# -----------------------------
# Ensemble
# -----------------------------
def predict_2026(inp: PredictionInput, weights: Dict[str, float] = None) -> PredictionBreakdown:
    weights = weights or DEFAULT_WEIGHTS.copy()

    # If job_description provided, enrich inputs
    role_key = normalize_role(inp.role)
    level = normalize_level(inp.level)
    skills = list(inp.skills)

    if inp.job_description:
        ext = extract_from_text(inp.job_description)
        role_key = ext["role_key"] or role_key
        level = ext["level"] or level
        skills = sorted(set(skills + ext["skills"]))

    if role_key not in BASELINES_2024:
        role_key = "cybersecurity_engineer"

    if level not in BASELINES_2024[role_key]:
        level = "mid"

    base_low, base_high = BASELINES_2024[role_key][level]

    # 2) CAGR
    cagr_low, cagr_high = apply_cagr(base_low, base_high, role_key)

    # 3) Inflation
    infl_low, infl_high = apply_inflation(cagr_low, cagr_high)

    # 4) Skills
    s_mult = compute_skills_multiplier(skills)

    # 5) Geo
    g_mult = compute_geo_multiplier(inp.state)

    # 6) Regression factor
    r_mult = regression_adjustment(role_key, level, inp.years_experience)

    # Combine: we build multiple candidate estimates, then weighted average
    # Candidate A: inflation-only
    a_low, a_high = infl_low, infl_high
    # Candidate B: inflation + skills
    b_low, b_high = infl_low * s_mult, infl_high * s_mult
    # Candidate C: inflation + geo
    c_low, c_high = infl_low * g_mult, infl_high * g_mult
    # Candidate D: inflation + skills + geo
    d_low, d_high = infl_low * s_mult * g_mult, infl_high * s_mult * g_mult
    # Candidate E: regression-adjusted (applied on top of D)
    e_low, e_high = d_low * r_mult, d_high * r_mult

    # Weighted ensemble (simple)
    # We treat cagr/inflation as already embedded, and weight skills/geo/regression signals.
    # Start from A and blend toward E.
    w_sk = weights["skills"]
    w_geo = weights["geo"]
    w_reg = weights["regression"]

    # Blend: A -> B -> D -> E
    low = (1 - w_sk) * a_low + w_sk * b_low
    high = (1 - w_sk) * a_high + w_sk * b_high

    low = (1 - w_geo) * low + w_geo * d_low
    high = (1 - w_geo) * high + w_geo * d_high

    low = (1 - w_reg) * low + w_reg * e_low
    high = (1 - w_reg) * high + w_reg * e_high

    mid = (low + high) / 2.0

    return PredictionBreakdown(
        baseline_low=float(base_low),
        baseline_high=float(base_high),
        cagr_low=float(cagr_low),
        cagr_high=float(cagr_high),
        inflation_low=float(infl_low),
        inflation_high=float(infl_high),
        skills_multiplier=float(s_mult),
        geo_multiplier=float(g_mult),
        regression_adjustment=float(r_mult),
        final_low=float(low),
        final_mid=float(mid),
        final_high=float(high),
    )

def normalize_role(role: str) -> str:
    r = (role or "").strip().lower()
    # direct alias hit
    if r in ROLE_ALIASES:
        return ROLE_ALIASES[r]
    # fuzzy contains
    for k, v in ROLE_ALIASES.items():
        if k in r:
            return v
    # already a key?
    if r.replace(" ", "_") in BASELINES_2024:
        return r.replace(" ", "_")
    return "cybersecurity_engineer"

def normalize_level(level: str) -> str:
    l = (level or "").strip().lower()
    if l in LEVEL_ALIASES:
        return LEVEL_ALIASES[l]
    for k, v in LEVEL_ALIASES.items():
        if k in l:
            return v
    return "mid"

def money(x: float) -> str:
    return f"${x:,.0f}"

def demo():
    examples = [
        PredictionInput(
            role="Cybersecurity Engineer",
            level="Mid",
            years_experience=4,
            state="TX",
            skills=["siem", "soar", "zero_trust", "cissp"],
            job_description="We need a mid-level security engineer with SIEM/SOAR, Zero Trust, and CISSP preferred."
        ),
        PredictionInput(
            role="Cloud Security Engineer",
            level="Senior",
            years_experience=9,
            state="CA",
            skills=["aws_security", "kubernetes", "terraform", "cnapp", "ccsp"],
            job_description="Senior Cloud Security Engineer - AWS, Kubernetes, Terraform, CNAPP, CCSP."
        ),
        PredictionInput(
            role="SOC Analyst",
            level="Entry",
            years_experience=1,
            state="REMOTE",
            skills=["siem", "edr", "security_plus"],
            job_description="Entry SOC analyst with EDR and SIEM experience. Security+ is a plus."
        ),
    ]

    for i, ex in enumerate(examples, 1):
        out = predict_2026(ex)
        print("=" * 72)
        print(f"Example {i}: {ex.role} ({ex.level}), {ex.state}, {ex.years_experience} yrs")
        print("Skills:", ", ".join(ex.skills))
        print(f"Predicted 2026 range: {money(out.final_low)} – {money(out.final_high)} (mid {money(out.final_mid)})")
        print("Breakdown:", {k: v for k, v in asdict(out).items() if k.endswith(("multiplier", "adjustment"))})

if __name__ == "__main__":
    demo()
