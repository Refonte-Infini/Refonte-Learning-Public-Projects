# Data Analytics Salary Guide Prediction (US, 2026)

## Overview
**Data Analytics Salary Guide Prediction** is a project designed to predict **US salaries for Data Analytics and related roles in 2026** using a **salary-guide-style modeling pipeline** inspired by Refonte’s framework.

Unlike projects that hardcode salary tables, this repo uses **legit, traceable wage data pulled live at runtime** from **O*NET OnLine National Wages (BLS-backed)**, then applies a configurable forecasting pipeline:

1. **Baseline Wages (Legit Source: O*NET / BLS)**  
   Pulls **annual wage percentiles** from O*NET’s “Save Table: CSV” export (United States row).  
   - **p10 (Annual Low 10%)** → Entry proxy  
   - **p50 (Annual Median 50%)** → Mid proxy  
   - **p90 (Annual High 90%)** → Senior proxy  

2. **Inflation Adjustment Model**  
   Applies an inflation multiplier to baseline wages (configurable).

3. **Demand Growth Model**  
   Applies market demand growth (configurable), with optional per-role demand bonus.

4. **Skills Premium Weighting Model**  
   Adds stacked skills premiums for high-demand analytics skills (SQL, Python, BI tools, Cloud, ML).

5. **Visualization + Reporting**  
   Generates:
   - **CSV salary dataset**
   - **HTML report** (tables + source links)
   - **Chart** (Entry/Mid/Senior bar chart)

This repository provides implementations in **Python, JavaScript, and Java**, each placed in dedicated folders (multi-language pattern consistent with the referenced Refonte repo).

---

## Legit Data Source (What we “took” and where)
All baseline salaries come from **O*NET OnLine “National Wages”** for a given O*NET code.

- O*NET wage page (human-readable):  
  `https://www.onetonline.org/link/localwages/{ONET_CODE}`

- O*NET wage export CSV (machine-readable):  
  `https://www.onetonline.org/link/localwagestable/{ONET_CODE}/LocalWages_{ONET_CODE}_US.csv?fmt=csv`

The code extracts the **United States** row and uses annual percentiles (p10/p50/p90).

---

## Roles Covered (Current Mapping)
Role → O*NET occupation code mapping is defined in each implementation:

- **Data Analyst (proxy: Operations Research Analysts)** → `15-2031.00`
- **Business Intelligence Analyst** → `15-2051.01`
- **Data Scientist** → `15-2051.00`
- **Data Engineer (proxy: Software Developers)** → `15-1252.00`

To add more roles, update `ROLE_TO_ONET` in the code.

---

## Project Structure
```
data-analytics-salary-guide-prediction/
├── python/
│ ├── main.py
│ 
├── javascript/
│ ├── main.js
│ ├── package.json
│ └── (generated outputs: salary_report.html, salary_data.csv, salary_chart.png, salary_chart_data.json)
├── java/
│ ├── SalaryGuide.java
│ ├── pom.xml
│ └── (generated outputs: salary_report.html, salary_data.csv, salary_chart.png)
└── README.md

``` 

---

## Installation and Usage

### Python
1. Navigate to python folder:

   ``` 
   cd Python
   ```


3. Run:

    ```
   python main.py
    ```

### JavaScript
1. Navigate:

   ```
    cd Javascript
   ```

2. Install:

    ```
   npm install
    ```

3. Run:

    ```
   node script.js
    ```
### Java
1. Navigate:
    ```
   cd java
    ```
2. Build &  Run:
   ```
   mvn -q -DskipTests package
   mvn -q exec:java -Dexec.mainClass=Main
   ```
   # How Predictions Work (High-Level)

## Inputs

- **Role** (mapped to an official O*NET occupation)  
- **Career level** (entry / mid / senior via percentiles)  
- **Inflation rate**  
- **Skills and technology premiums**  
- **Demand and geographic multipliers**  
- **Optional job description text** (NLP layer)

## Outputs

- **Predicted 2026 salary ranges** (entry / mid / senior) in USD  
- **A comparative salary visualization** (PNG) across roles and levels

## Notes / Configuration

This project ships with:

- O*NET occupation mappings (**editable**)  
- Inflation multiplier (**editable**)  
- CAGR-like assumptions (**editable**)  
- Skills premiums (**editable**)  
- Demand & geographic multipliers (**editable**)

The architecture is intentionally modular so you can:

- Replace synthetic CAGR logic with real historical wage series  
- Swap proxy roles with more granular occupations  
- Plug in internal survey or proprietary datasets later

## License

This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.

## Contact

For questions, improvements, or extensions, please open an issue or contact the Refonte team at  
[contact@refonteinfini.com](mailto:contact@refonteinfini.com)