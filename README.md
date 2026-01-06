# Cybersecurity Engineering Salary Guide Prediction (US, 2026)

## Overview
Cybersecurity Engineering Salary Guide Prediction is a project designed to predict **US salaries for Cybersecurity Engineering and related roles in 2026** using a multi-model ensemble approach inspired by Refonte’s salary-guide framework. The system blends six analytical components:

1. **BERT / NLP Extraction**  
   Extracts structured information (role, level, skills, location hints) from unstructured text (job descriptions, resumes, postings).
2. **CAGR Model**  
   Applies compound annual growth based on historical baseline ranges (configured in code).
3. **Inflation Adjustment Model**  
   Adjusts salary values for inflation (configurable CPI multiplier).
4. **Skills Premium Weighting Model**  
   Adds compensation premiums for high-demand security skills/certs (cloud security, zero trust, DFIR, etc.).
5. **Demand & Geographic Growth Model**  
   Adjusts for US region/state market demand and cost-of-labor differences.
6. **Weighted Regression / Ensemble Model**  
   Combines the above signals into a final predicted range.

This repository provides implementations in **Python, JavaScript, and Java**, each placed in dedicated folders (same multi-language pattern as the referenced Refonte repo). 

---

## Roles Covered (examples)
- Cybersecurity Engineer / Security Engineer
- Cloud Security Engineer
- DevSecOps Engineer
- Application Security Engineer (AppSec)
- SOC Analyst (Tier 1/2/3)
- Incident Response (IR) Engineer / DFIR
- Threat Hunter
- Penetration Tester / Red Team
- Security Architect
- GRC Analyst (adjacent “related role”)

---

## Project Structure
```
cybersecurity-salary-guide-prediction/
├── python/
│   ├── main.py
│   ├── chart.py
│   └── requirements.txt
├── javascript/
│   ├── chart.html
│   ├── main.js
│   └── package.json
├── java/
│   └── Main.java
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

2. Compile:

    ```
   javac Main.java
   ```

3. Run:

    ```
   java Main
    ```

---

## How predictions work (high-level)
Inputs:
- Role + level (entry / mid / senior)
- Years of experience
- Location (US state or “remote”)
- Skills and certifications
- Optional job description text

Outputs:
- Predicted 2026 salary range (low/median/high) in USD
- A breakdown of model contributions (skills premium, geo adjustment, etc.)

---

## Notes / Configuration
This repo ships with:
- A **baseline salary table** for common cybersecurity roles & levels (editable)
- An **inflation multiplier** (editable)
- A **CAGR rate** per role family (editable)
- **Skills premiums** and **geographic multipliers** (editable)

You can plug in real survey datasets later; the architecture is designed to be swapped from “configured baselines” to “learned baselines” without changing the public API.

---

## License
This project is licensed under the MIT License. See LICENSE for details.

---

## Contact
For questions or feedback, please open an issue or reach out via email at [contact@refonteinfini.com]. 