import matplotlib.pyplot as plt
import numpy as np

# ✅ IMPORT FROM YOUR MODEL FILE
from main import PredictionInput, predict_2026


def plot_salary_bars_2026(examples):
    results = [predict_2026(ex) for ex in examples]

    labels = [f"{ex.role}\n({ex.state}, {ex.level})" for ex in examples]
    mids = np.array([r.final_mid for r in results])
    lows = np.array([r.final_low for r in results])
    highs = np.array([r.final_high for r in results])

    yerr = np.vstack([mids - lows, highs - mids])
    x = np.arange(len(labels))

    plt.figure(figsize=(12, 6))
    plt.bar(x, mids, yerr=yerr, capsize=6)
    plt.xticks(x, labels)
    plt.ylabel("Predicted 2026 Salary (USD)")
    plt.title("Predicted 2026 Cybersecurity Salaries")
    plt.tight_layout()
    plt.show()


if __name__ == "__main__":
    examples = [
        PredictionInput(
            role="Cybersecurity Engineer",
            level="Mid",
            years_experience=4,
            state="TX",
            skills=["siem", "soar", "zero_trust", "cissp"]
        ),
        PredictionInput(
            role="Cloud Security Engineer",
            level="Senior",
            years_experience=9,
            state="CA",
            skills=["aws_security", "kubernetes", "terraform", "cnapp", "ccsp"]
        ),
        PredictionInput(
            role="SOC Analyst",
            level="Entry",
            years_experience=1,
            state="REMOTE",
            skills=["siem", "edr", "security_plus"]
        ),
    ]

    plot_salary_bars_2026(examples)
