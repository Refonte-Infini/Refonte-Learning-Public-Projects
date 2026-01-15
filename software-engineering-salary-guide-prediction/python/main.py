import re
import requests
import pandas as pd
import matplotlib.pyplot as plt
from bs4 import BeautifulSoup

# ============================================================
# LEGIT DATA SOURCE (USED LIVE AT RUNTIME)
# ============================================================
# We fetch wage percentiles from O*NET OnLine "National Wages" pages.
# O*NET pages state the wage data source is Bureau of Labor Statistics (BLS),
# and the pages we verified show "Source: Bureau of Labor Statistics 2024 wage data."
# Examples:
# - Software Developers: https://www.onetonline.org/link/localwages/15-1252.00  :contentReference[oaicite:2]{index=2}
# - Information Security Analysts: https://www.onetonline.org/link/localwages/15-1212.00 :contentReference[oaicite:3]{index=3}
# - Network & Computer Systems Administrators: https://www.onetonline.org/link/localwages/15-1244.00 :contentReference[oaicite:4]{index=4}
#
# We use annual wage percentiles:
# - 10th percentile  -> "Entry" proxy
# - 50th (median)    -> "Mid" proxy
# - 90th percentile  -> "Senior" proxy
# Percentile wages concept is defined by BLS. :contentReference[oaicite:5]{index=5}
# ============================================================

# -----------------------------
# ROLE -> OFFICIAL O*NET CODE MAPPING
# (This is where the "data you took" is defined for each role)
# -----------------------------
ROLE_TO_ONET = {
    # Direct matches
    "Software Developer": "15-1252.00",
    "Web Developer": "15-1254.00",
    "Information Security Analyst": "15-1212.00",
    "Data Scientist (proxy for ML Engineer)": "15-2051.00",

    # Proxies (closest official occupation buckets)
    "DevOps Engineer (proxy: Software Developers)": "15-1252.00",
    "Cloud Engineer (proxy: Network & Computer Systems Admins)": "15-1244.00",
}

ONET_WAGES_URL = "https://www.onetonline.org/link/localwages/{code}"

# -----------------------------
# Factors (replace with your real factors if you have them)
# -----------------------------
# Demand and geographic factors per role (simple placeholders)
DEMAND_FACTOR = {
    "Software Developer": 0.10,
    "Web Developer": 0.08,
    "Information Security Analyst": 0.13,
    "Data Scientist (proxy for ML Engineer)": 0.12,
    "DevOps Engineer (proxy: Software Developers)": 0.12,
    "Cloud Engineer (proxy: Network & Computer Systems Admins)": 0.11,
}

GEOGRAPHIC_FACTOR = {
    "Software Developer": 0.05,
    "Web Developer": 0.05,
    "Information Security Analyst": 0.06,
    "Data Scientist (proxy for ML Engineer)": 0.06,
    "DevOps Engineer (proxy: Software Developers)": 0.06,
    "Cloud Engineer (proxy: Network & Computer Systems Admins)": 0.05,
}

# Skills premium map (example; tune for your organization)
SKILLS_PREMIUMS = {
    "AWS": 0.04,
    "Kubernetes": 0.05,
    "Terraform": 0.03,
    "Security": 0.03,
    "SystemDesign": 0.04,
    "MachineLearning": 0.06,
}

# -----------------------------
# Utility: fetch + parse O*NET wages
# -----------------------------
def _money_to_float(s: str) -> float:
    # "$79,850" -> 79850.0
    return float(s.replace("$", "").replace(",", "").strip())

def fetch_onet_percentile_wages(onet_code: str) -> dict:
    """
    Fetches United States annual wages at:
      - Low (10%)
      - Median (50%)
      - High (90%)
    from O*NET localwages table.

    Returns:
      {
        "source_url": "...",
        "p10": 0.0,
        "p50": 0.0,
        "p90": 0.0
      }
    """
    url = ONET_WAGES_URL.format(code=onet_code)
    resp = requests.get(url, timeout=30)
    resp.raise_for_status()

    soup = BeautifulSoup(resp.text, "html.parser")
    text = soup.get_text(" ", strip=True)

    # The page includes a table row for "United States" with Annual Low (10%), Annual Median (50%), Annual High (90%).
    # We parse the first occurrence of that pattern.
    # Example strings on the page look like:
    # "United States $79,850 $... $... $... $211,450" (varies by occupation)
    # We'll extract the first 3 percentile columns we need: 10%, 50%, 90%.
    #
    # More robust approach: find the "United States" row in HTML table,
    # but the table markup can vary; text-based parsing is pragmatic here.
    m = re.search(
        r"United States\s+\$[\d,]+\s+\$[\d,]+\s+\$[\d,]+\s+\$[\d,]+\s+\$[\d,]+",
        text
    )
    if not m:
        raise ValueError(f"Could not locate United States wage row for {onet_code} at {url}")

    row = m.group(0)
    dollars = re.findall(r"\$[\d,]+", row)
    if len(dollars) < 5:
        raise ValueError(f"Unexpected wage row format for {onet_code}: {row}")

    # O*NET table columns are:
    # Annual Low (10%), Annual Q L (25%), Annual Median (50%), Annual Q U (75%), Annual High (90%)
    p10 = _money_to_float(dollars[0])
    p50 = _money_to_float(dollars[2])
    p90 = _money_to_float(dollars[4])

    return {"source_url": url, "p10": p10, "p50": p50, "p90": p90}

# -----------------------------
# Models (like your example)
# -----------------------------
def inflation_adjustment(salary, inflation_rate):
    return salary * (1 + inflation_rate)

def skills_premium_adjustment(base_salary, skill_factors):
    total_premium = sum(skill_factors.values())
    return base_salary * (1 + total_premium)

def demand_geographic_adjustment(base_salary, demand_factor, geographic_factor):
    return base_salary * (1 + demand_factor + geographic_factor)

# -----------------------------
# Charting
# -----------------------------
def plot_salary_chart(df, year_label="2026", save_path=None):
    roles = df["Role"].tolist()
    x = range(len(roles))
    width = 0.25

    entry = df[f"Entry-Level {year_label}"].tolist()
    mid = df[f"Mid-Level {year_label}"].tolist()
    senior = df[f"Senior-Level {year_label}"].tolist()

    plt.figure(figsize=(14, 6))
    plt.bar([i - width for i in x], entry, width=width, label="Entry (p10 proxy)")
    plt.bar(list(x), mid, width=width, label="Mid (p50 proxy)")
    plt.bar([i + width for i in x], senior, width=width, label="Senior (p90 proxy)")

    plt.xticks(list(x), roles, rotation=25, ha="right")
    plt.ylabel("Salary (USD)")
    plt.title(f"Software Engineering Salaries ({year_label}) — O*NET/BLS Percentiles + Adjustments")
    plt.legend()
    plt.tight_layout()

    if save_path:
        plt.savefig(save_path, dpi=200)
        print(f"\nChart saved to: {save_path}")
    else:
        plt.show()

    plt.close()

# -----------------------------
# Main
# -----------------------------
def main():
    target_year_label = "2026"

    # Change these assumptions if you want
    inflation_rate = 0.03

    # 1) Pull legit wages for each role from O*NET (BLS-backed)
    rows = []
    print("\n=== DATA SOURCES USED (LIVE) ===")
    for role, onet_code in ROLE_TO_ONET.items():
        wages = fetch_onet_percentile_wages(onet_code)
        print(f"- {role} -> O*NET {onet_code} -> {wages['source_url']}")

        rows.append({
            "Role": role,
            "O*NET Code": onet_code,
            "Source URL": wages["source_url"],
            # Base wages from legit source:
            "Entry-Level Base (p10)": wages["p10"],
            "Mid-Level Base (p50)": wages["p50"],
            "Senior-Level Base (p90)": wages["p90"],
        })

    base_df = pd.DataFrame(rows)

    # 2) Add demand + geo factors
    base_df["Demand Factor"] = base_df["Role"].map(DEMAND_FACTOR).fillna(0.0)
    base_df["Geographic Factor"] = base_df["Role"].map(GEOGRAPHIC_FACTOR).fillna(0.0)

    # 3) Apply modeling pipeline (inflation -> skills -> demand+geo)
    entry_pred, mid_pred, senior_pred = [], [], []
    for _, r in base_df.iterrows():
        entry = inflation_adjustment(r["Entry-Level Base (p10)"], inflation_rate)
        mid = inflation_adjustment(r["Mid-Level Base (p50)"], inflation_rate)
        senior = inflation_adjustment(r["Senior-Level Base (p90)"], inflation_rate)

        entry = skills_premium_adjustment(entry, SKILLS_PREMIUMS)
        mid = skills_premium_adjustment(mid, SKILLS_PREMIUMS)
        senior = skills_premium_adjustment(senior, SKILLS_PREMIUMS)

        entry = demand_geographic_adjustment(entry, r["Demand Factor"], r["Geographic Factor"])
        mid = demand_geographic_adjustment(mid, r["Demand Factor"], r["Geographic Factor"])
        senior = demand_geographic_adjustment(senior, r["Demand Factor"], r["Geographic Factor"])

        entry_pred.append(entry)
        mid_pred.append(mid)
        senior_pred.append(senior)

    base_df[f"Entry-Level {target_year_label}"] = entry_pred
    base_df[f"Mid-Level {target_year_label}"] = mid_pred
    base_df[f"Senior-Level {target_year_label}"] = senior_pred

    # 4) Print exactly what we used
    print("\n=== BASE WAGES PULLED (O*NET/BLS-backed) ===")
    print(base_df[[
        "Role", "O*NET Code",
        "Entry-Level Base (p10)", "Mid-Level Base (p50)", "Senior-Level Base (p90)",
        "Source URL"
    ]].to_string(index=False))

    print("\n=== PREDICTED SALARIES (after inflation + skills + demand + geo) ===")
    print(base_df[[
        "Role",
        f"Entry-Level {target_year_label}", f"Mid-Level {target_year_label}", f"Senior-Level {target_year_label}"
    ]].to_string(index=False))

    # 5) Chart
    chart_df = base_df[["Role", f"Entry-Level {target_year_label}", f"Mid-Level {target_year_label}", f"Senior-Level {target_year_label}"]]
    plot_salary_chart(chart_df, year_label=target_year_label, save_path="software_engineering_bls_onet_2026.png")


if __name__ == "__main__":
    main()
