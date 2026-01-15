# Software Engineering Salary Guide Prediction (US, 2026)

## Overview
Software Engineering Salary Guide Prediction is a project designed to predict **US salaries for Software Engineering and related roles in 2026** using a **multi-model ensemble approach** inspired by Refonte’s salary-guide framework.

The system combines **legit government-backed wage baselines** (via O*NET / BLS data) with configurable economic and market models to produce forward-looking salary predictions and charts.

The pipeline blends six analytical components:

1. **BERT / NLP Extraction (Optional)**  
   Extracts structured signals (role, level, salary hints) from unstructured text such as job descriptions.  
   *Included as a stub/off-by-default layer for extensibility.*
2. **CAGR Model (CAGR-like)**  
   Applies compound annual growth based on configurable historical assumptions (replaceable with real time-series data).
3. **Inflation Adjustment Model**  
   Adjusts salary values using an inflation multiplier (CPI-style).
4. **Skills Premium Weighting Model**  
   Adds compensation premiums for high-demand software skills (cloud, Kubernetes, system design, ML, security, etc.).
5. **Demand & Geographic Growth Model**  
   Adjusts salaries based on role demand and geographic market pressure.
6. **Weighted Regression / Ensemble Model**  
   Combines all signals into a final predicted salary range.

This repository provides implementations in **Python, JavaScript, and Java**, following the same multi-language structure used in Refonte’s reference salary-guide repositories.

---

## Data Sources (Legit)
Baseline wages are pulled from **O*NET OnLine National Wages pages**, which explicitly cite **US Bureau of Labor Statistics (BLS)** wage data.

- Source pattern:
https://www.onetonline.org/link/localwages/{O_NET_CODE}


- Career-level proxies:
- **Entry-level** → 10th percentile (p10)
- **Mid-level** → 50th percentile / median (p50)
- **Senior-level** → 90th percentile (p90)

> Government datasets do not publish “Entry / Mid / Senior” labels directly for modern software roles. This project uses **transparent occupation proxies** and prints the exact mapping and source URLs when executed.

---

## Roles Covered (examples)
- Software Engineer / Software Developer
- Backend Engineer
- Frontend Engineer
- Full-Stack Developer
- DevOps Engineer (proxy-based)
- Cloud Engineer (proxy-based)
- Machine Learning Engineer (Data Scientist proxy)
- Information Security Analyst (adjacent role)

---

## Project Structure
```
software-engineering-salary-guide-prediction/
├── python/
│ ├── main.py
│ └── requirements.txt
├── javascript/
│ ├── main.mjs
├── java/
│ ├── Main.java
└── README.md

``` 

---

## Installation and Usage

### Python
1. Navigate to python folder:

   ``` 
   cd python
   ```

2. Install:

    ```
   pip install -r requirements.txt
    ```

3. Run:

    ```
   python main.py
    ```

### JavaScript
1. Navigate:

   ```
    cd javascript
   ```

2. Install:

    ```
   npm install
    ```

3. Run:

    ```
   node main.js
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
