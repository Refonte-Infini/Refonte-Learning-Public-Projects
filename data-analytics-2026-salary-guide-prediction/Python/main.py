import io
import requests
import pandas as pd
import matplotlib.pyplot as plt

# ============================================================
# SALARY GUIDE PIPELINE (LinkedIn salary guide you provided)
# ============================================================
# Article:
# https://www.linkedin.com/pulse/data-analytics-related-roles-salary-your-ultimate-guide-8umtf/
#
# The guide states example model parameters inside its model slides:
# - Inflation ~3% annually (IMF/World Bank based) used in:
#   Adjusted Salary = Nominal Salary × (1 + Inflation Rate)
#   (see the guide's "Inflation Adjustment Formula" slide image)
# - Demand growth ~12% used in:
#   Demand Growth = Base Salary × (1 + Growth Rate)
#   (see the guide's "Demand Growth Model" slide image)
#
# We use those guide parameters below as the MODEL assumptions.
# ============================================================

# ============================================================
# LEGIT BASE SALARY SOURCE (USED LIVE AT RUNTIME)
# ============================================================
# The LinkedIn guide cites BLS among its trusted sources.
# To keep the pipeline fully reproducible and auditable at runtime, we pull
# base salary percentiles from O*NET "National Wages" (BLS-backed) CSV export.
#
# We use:
# - Annual Low (10%)    -> Entry proxy
# - Annual Median (50%) -> Mid proxy
# - Annual High (90%)   -> Senior proxy
# ============================================================

# -----------------------------
# ROLE -> OFFICIAL O*NET CODE MAPPING
# (This is where the "data you took" is defined for each role)
# -----------------------------
ROLE_TO_ONET = {
    "Data Analyst (proxy: Operations Research Analysts)": "15-2031.00",
    "Business Intelligence Analyst": "15-2051.01",
    "Data Scientist": "15-2051.00",
    "Data Engineer (proxy: Software Developers)": "15-1252.00",
}

# Human-readable wage page
ONET_PAGE_URL = "https://www.onetonline.org/link/localwages/{code}"

# CSV export (stable parsing)
ONET_CSV_URL = "https://www.onetonline.org/link/localwagestable/{code}/LocalWages_{code}_US.csv?fmt=csv"

# -----------------------------
# LinkedIn-guide parameters (from the guide’s model slides)
# -----------------------------
INFLATION_RATE = 0.03   # ~3% annually (guide’s inflation slide)
DEMAND_GROWTH  = 0.12   # ~12% growth rate (guide’s demand slide)

SKILLS_PREMIUMS = {
    "SQL": 0.04,
    "Python": 0.05,
    "PowerBI/Tableau": 0.04,
    "Cloud (AWS/Azure/GCP)": 0.05,
    "ML": 0.06,
}

ROLE_DEMAND_BONUS = {
    "Data Analyst (proxy: Operations Research Analysts)": 0.00,
    "Business Intelligence Analyst": 0.01,
    "Data Scientist": 0.02,
    "Data Engineer (proxy: Software Developers)": 0.02,
}

# -----------------------------
# Salary guide pipeline
# -----------------------------
def inflation_adjustment(salary: float, inflation_rate: float) -> float:
    return salary * (1.0 + inflation_rate)

def demand_growth_adjustment(salary: float, demand_growth: float, role_bonus: float = 0.0) -> float:
    return salary * (1.0 + demand_growth + role_bonus)

def skills_premium_adjustment(salary: float, skill_premiums: dict) -> float:
    return salary * (1.0 + sum(skill_premiums.values()))

# -----------------------------
# Fetch + parse O*NET wages (CSV) — NO FALLBACK (legit-only)
# -----------------------------
def fetch_onet_percentile_wages_from_csv(onet_code: str) -> dict:
    page_url = ONET_PAGE_URL.format(code=onet_code)
    csv_url  = ONET_CSV_URL.format(code=onet_code)

    headers = {
        "User-Agent": "Mozilla/5.0",
        "Accept": "text/csv,*/*",
    }
    resp = requests.get(csv_url, headers=headers, timeout=30)
    resp.raise_for_status()

    df = pd.read_csv(io.StringIO(resp.text))

    def find_col_contains(substr: str) -> str:
        for c in df.columns:
            if substr.lower() in c.lower():
                return c
        raise KeyError(f"Missing required column containing '{substr}'. Found: {list(df.columns)}")

    loc_col = find_col_contains("Location")
    p10_col = find_col_contains("Annual Low")
    p50_col = find_col_contains("Annual Median")
    p90_col = find_col_contains("Annual High")

    us = df[df[loc_col].astype(str).str.strip() == "United States"]
    if us.empty:
        raise ValueError(f'No "United States" row found for {onet_code} in {csv_url}')

    def money_to_float(x):
        s = str(x).replace("$", "").replace(",", "").strip()
        return float(s)

    p10 = money_to_float(us.iloc[0][p10_col])
    p50 = money_to_float(us.iloc[0][p50_col])
    p90 = money_to_float(us.iloc[0][p90_col])

    if min(p10, p50, p90) <= 0:
        raise ValueError(f"Invalid wage values parsed for {onet_code}: p10={p10}, p50={p50}, p90={p90}")

    return {
        "source_page_url": page_url,
        "source_csv_url": csv_url,
        "p10": p10,
        "p50": p50,
        "p90": p90,
    }

# -----------------------------
# Chart
# -----------------------------
def plot_salary_chart(df: pd.DataFrame, year_label="2026", save_path=None):
    roles = df["Role"].tolist()
    x = range(len(roles))
    width = 0.25

    entry = df[f"Entry-Level {year_label}"].tolist()
    mid   = df[f"Mid-Level {year_label}"].tolist()
    senior= df[f"Senior-Level {year_label}"].tolist()

    plt.figure(figsize=(14, 6))
    plt.bar([i - width for i in x], entry, width=width, label="Entry (p10 proxy)")
    plt.bar(list(x), mid, width=width, label="Mid (p50 proxy)")
    plt.bar([i + width for i in x], senior, width=width, label="Senior (p90 proxy)")

    plt.xticks(list(x), roles, rotation=20, ha="right")
    plt.ylabel("Salary (USD)")
    plt.title(f"Data Analytics Roles Salaries ({year_label}) — Base (O*NET/BLS) + LinkedIn Guide Adjustments")
    plt.legend()
    plt.tight_layout()

    if save_path:
        plt.savefig(save_path, dpi=200)
        print(f"\nChart saved to: {save_path}")
    else:
        plt.show()
    plt.close()

def main():
    target_year_label = "2026"

    print("\n=== MODEL SOURCE (LinkedIn salary guide) ===")
    print("Guide URL:")
    print("  https://www.linkedin.com/pulse/data-analytics-related-roles-salary-your-ultimate-guide-8umtf/")
    print(f"Parameters taken from the guide’s model slides:")
    print(f"  Inflation rate: {INFLATION_RATE:.2%}")
    print(f"  Demand growth:  {DEMAND_GROWTH:.2%}")
    print(f"  Skills premiums sum: {sum(SKILLS_PREMIUMS.values()):.2%}")

    print("\n=== DATA SOURCES USED (LIVE BASE SALARIES) ===")
    print("Base salaries are pulled at runtime from O*NET wage tables (BLS-backed) via CSV export.")

    rows = []
    errors = []

    for role, code in ROLE_TO_ONET.items():
        print(f"\nFetching base wages for: {role} -> O*NET {code}")
        try:
            wages = fetch_onet_percentile_wages_from_csv(code)
            print(f"  Page: {wages['source_page_url']}")
            print(f"  CSV : {wages['source_csv_url']}")
            print(f"  p10={wages['p10']:.0f}  p50={wages['p50']:.0f}  p90={wages['p90']:.0f}")

            rows.append({
                "Role": role,
                "O*NET Code": code,
                "Source Page URL": wages["source_page_url"],
                "Source CSV URL": wages["source_csv_url"],
                "Entry-Level Base (p10)": wages["p10"],
                "Mid-Level Base (p50)": wages["p50"],
                "Senior-Level Base (p90)": wages["p90"],
            })
        except Exception as e:
            errors.append({
                "Role": role,
                "O*NET Code": code,
                "Error": str(e),
                "Page URL": ONET_PAGE_URL.format(code=code),
                "CSV URL": ONET_CSV_URL.format(code=code),
            })
            print(f"  ❌ FAILED (skipping): {e}")

    if not rows:
        raise RuntimeError("No roles could be fetched. Check connectivity or O*NET availability.")

    base_df = pd.DataFrame(rows)

    # Apply pipeline (as per guide concept): inflation -> demand -> skills
    entry_pred, mid_pred, senior_pred = [], [], []
    for _, r in base_df.iterrows():
        role = r["Role"]
        role_bonus = ROLE_DEMAND_BONUS.get(role, 0.0)

        entry = inflation_adjustment(r["Entry-Level Base (p10)"], INFLATION_RATE)
        mid   = inflation_adjustment(r["Mid-Level Base (p50)"], INFLATION_RATE)
        senior= inflation_adjustment(r["Senior-Level Base (p90)"], INFLATION_RATE)

        entry = demand_growth_adjustment(entry, DEMAND_GROWTH, role_bonus)
        mid   = demand_growth_adjustment(mid, DEMAND_GROWTH, role_bonus)
        senior= demand_growth_adjustment(senior, DEMAND_GROWTH, role_bonus)

        entry = skills_premium_adjustment(entry, SKILLS_PREMIUMS)
        mid   = skills_premium_adjustment(mid, SKILLS_PREMIUMS)
        senior= skills_premium_adjustment(senior, SKILLS_PREMIUMS)

        entry_pred.append(entry)
        mid_pred.append(mid)
        senior_pred.append(senior)

    base_df[f"Entry-Level {target_year_label}"] = entry_pred
    base_df[f"Mid-Level {target_year_label}"] = mid_pred
    base_df[f"Senior-Level {target_year_label}"] = senior_pred

    print("\n=== BASE WAGES PULLED (LIVE) ===")
    print(base_df[[
        "Role", "O*NET Code",
        "Entry-Level Base (p10)", "Mid-Level Base (p50)", "Senior-Level Base (p90)",
        "Source Page URL", "Source CSV URL"
    ]].to_string(index=False))

    print("\n=== PREDICTED SALARIES (after inflation + demand + skills) ===")
    print(base_df[[
        "Role",
        f"Entry-Level {target_year_label}",
        f"Mid-Level {target_year_label}",
        f"Senior-Level {target_year_label}",
    ]].to_string(index=False))

    base_df.to_csv("data_analytics_salary_guide_2026_data.csv", index=False)
    print("\nSaved data table to: data_analytics_salary_guide_2026_data.csv")

    chart_df = base_df[[
        "Role",
        f"Entry-Level {target_year_label}",
        f"Mid-Level {target_year_label}",
        f"Senior-Level {target_year_label}",
    ]]
    plot_salary_chart(chart_df, year_label=target_year_label, save_path="data_analytics_salary_guide_2026.png")

    if errors:
        pd.DataFrame(errors).to_json("data_fetch_errors.json", orient="records", indent=2)
        print("\n⚠️ Some roles failed. See: data_fetch_errors.json")

if __name__ == "__main__":
    main()
