#!/usr/bin/env python3
import math
import json
from typing import List, Dict, Tuple

# Charts output
OUT_JSON = "salary_guide_2026.json"
OUT_BAR = "chart_2026_mid_by_role.png"
OUT_RANGE = "chart_2026_low_high_by_role.png"

# --- Model config ---
CFG = {
    "base_year": 2025,
    "target_year": 2026,
    "inflation_rate": 0.03,
    "cagr_by_level": {
        "Entry": 0.06, "Mid": 0.07, "Senior": 0.08, "Lead": 0.085, "Manager": 0.085, "Director": 0.09
    },
    "skill_premium": {
        "Python": 0.03, "SQL": 0.02, "Tableau": 0.02, "Power BI": 0.02, "Looker": 0.02,
        "dbt": 0.03, "Snowflake": 0.03, "BigQuery": 0.03, "Spark": 0.03,
        "Machine Learning": 0.04, "Experimentation": 0.03, "DAX": 0.02, "Excel": 0.01, "Leadership": 0.015
    },
    "location_multiplier": {"US": 1.00, "EU": 0.85, "UK": 0.90, "CA": 0.90, "Remote": 0.95, "MEA": 0.65, "APAC": 0.80}
}

# --- Built-in baselines (edit if you want exact values) ---
BASELINES_2025 = [
    {"role": "Data Analyst",              "level": "Mid",     "base_low": 65000,  "base_high": 95000,  "skills": ["SQL", "Excel", "Power BI"],          "location": "US", "demand_index": 1.10},
    {"role": "BI Analyst",                "level": "Mid",     "base_low": 70000,  "base_high": 105000, "skills": ["SQL", "Power BI", "DAX"],            "location": "US", "demand_index": 1.08},
    {"role": "Business Analyst",          "level": "Mid",     "base_low": 70000,  "base_high": 110000, "skills": ["SQL", "Excel"],                     "location": "US", "demand_index": 1.06},
    {"role": "Product Analyst",           "level": "Mid",     "base_low": 80000,  "base_high": 125000, "skills": ["SQL", "Experimentation"],            "location": "US", "demand_index": 1.12},
    {"role": "Analytics Engineer",        "level": "Senior",  "base_low": 110000, "base_high": 165000, "skills": ["SQL", "Python", "dbt", "Snowflake"], "location": "US", "demand_index": 1.18},
    {"role": "Data Engineer",             "level": "Senior",  "base_low": 115000, "base_high": 175000, "skills": ["SQL", "Python", "Spark"],            "location": "US", "demand_index": 1.17},
    {"role": "Data Scientist",            "level": "Senior",  "base_low": 120000, "base_high": 180000, "skills": ["Python", "Machine Learning"],         "location": "US", "demand_index": 1.15},
    {"role": "Machine Learning Engineer", "level": "Senior",  "base_low": 140000, "base_high": 210000, "skills": ["Python", "Machine Learning"],         "location": "US", "demand_index": 1.20},
    {"role": "BI Developer",              "level": "Senior",  "base_low": 90000,  "base_high": 140000, "skills": ["SQL", "Power BI"],                   "location": "US", "demand_index": 1.10},
    {"role": "Marketing Analyst",         "level": "Mid",     "base_low": 65000,  "base_high": 100000, "skills": ["SQL", "Excel"],                     "location": "US", "demand_index": 1.07},
    {"role": "AI Analyst",                "level": "Mid",     "base_low": 85000,  "base_high": 135000, "skills": ["SQL", "Python"],                    "location": "US", "demand_index": 1.13},
    {"role": "Analytics Manager",         "level": "Manager", "base_low": 130000, "base_high": 200000, "skills": ["SQL", "Leadership"],                 "location": "US", "demand_index": 1.12},
]

def years_between(base_year: int, target_year: int) -> int:
    return max(0, target_year - base_year)

def apply_cagr(amount: float, cagr: float, years: int) -> float:
    return amount * ((1.0 + cagr) ** years)

def apply_inflation(amount: float, inflation: float, years: int) -> float:
    return amount * ((1.0 + inflation) ** years)

def skills_multiplier(skills: List[str], premiums: Dict[str, float]) -> float:
    mult = 1.0
    for s in skills:
        mult *= (1.0 + premiums.get(s, 0.0))
    return mult

def predict(row: Dict) -> Tuple[float, float, float]:
    yrs = years_between(CFG["base_year"], CFG["target_year"])
    cagr = CFG["cagr_by_level"].get(row["level"], 0.06)

    low = apply_cagr(row["base_low"], cagr, yrs)
    high = apply_cagr(row["base_high"], cagr, yrs)

    low = apply_inflation(low, CFG["inflation_rate"], yrs)
    high = apply_inflation(high, CFG["inflation_rate"], yrs)

    sm = skills_multiplier(row["skills"], CFG["skill_premium"])
    low *= sm
    high *= sm

    lm = CFG["location_multiplier"].get(row["location"], 0.90)
    low *= lm * row["demand_index"]
    high *= lm * row["demand_index"]

    mid = (low + high) / 2.0
    return low, high, mid

def main():
    results = []
    for r in BASELINES_2025:
        low, high, mid = predict(r)
        results.append({
            "role": r["role"],
            "level": r["level"],
            "base_year": CFG["base_year"],
            "target_year": CFG["target_year"],
            "location": r["location"],
            "skills": r["skills"],
            "demand_index": r["demand_index"],
            "base_low": r["base_low"],
            "base_high": r["base_high"],
            "projected_low": round(low, 2),
            "projected_high": round(high, 2),
            "projected_mid": round(mid, 2),
        })

    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)

    # --- Charts ---
    import matplotlib.pyplot as plt

    roles = [x["role"] for x in results]
    mids = [x["projected_mid"] for x in results]
    lows = [x["projected_low"] for x in results]
    highs = [x["projected_high"] for x in results]

    # 1) Bar chart (mid)
    plt.figure(figsize=(14, 7))
    plt.bar(roles, mids)
    plt.xticks(rotation=45, ha="right")
    plt.title("Projected 2026 Mid Salary by Role")
    plt.ylabel("Salary (USD)")
    plt.tight_layout()
    plt.savefig(OUT_BAR, dpi=200)
    plt.close()

    # 2) Range chart (low-high) using error bars around mid
    plt.figure(figsize=(14, 7))
    yerr_lower = [m - l for m, l in zip(mids, lows)]
    yerr_upper = [h - m for h, m in zip(highs, mids)]
    plt.errorbar(roles, mids, yerr=[yerr_lower, yerr_upper], fmt="o", capsize=6)
    plt.xticks(rotation=45, ha="right")
    plt.title("Projected 2026 Salary Range (Low–High) by Role")
    plt.ylabel("Salary (USD)")
    plt.tight_layout()
    plt.savefig(OUT_RANGE, dpi=200)
    plt.close()

    print(f"✅ Wrote {OUT_JSON}")
    print(f"✅ Saved chart: {OUT_BAR}")
    print(f"✅ Saved chart: {OUT_RANGE}")

if __name__ == "__main__":
    main()
