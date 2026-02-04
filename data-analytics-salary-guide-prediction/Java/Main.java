import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import javax.imageio.ImageIO;

public class Main {
    static final String OUT_JSON = "salary_guide_2026.json";
    static final String OUT_BAR = "chart_2026_mid_by_role.png";
    static final String OUT_RANGE = "chart_2026_low_high_by_role.png";

    static class CFG {
        static final int baseYear = 2025;
        static final int targetYear = 2026;
        static final double inflationRate = 0.03;

        static final Map<String, Double> cagrByLevel = Map.of(
                "Entry", 0.06, "Mid", 0.07, "Senior", 0.08, "Lead", 0.085, "Manager", 0.085, "Director", 0.09
        );

        static final Map<String, Double> skillPremium = Map.ofEntries(
                Map.entry("Python", 0.03),
                Map.entry("SQL", 0.02),
                Map.entry("Tableau", 0.02),
                Map.entry("Power BI", 0.02),
                Map.entry("Looker", 0.02),
                Map.entry("dbt", 0.03),
                Map.entry("Snowflake", 0.03),
                Map.entry("BigQuery", 0.03),
                Map.entry("Spark", 0.03),
                Map.entry("Machine Learning", 0.04),
                Map.entry("Experimentation", 0.03),
                Map.entry("DAX", 0.02),
                Map.entry("Excel", 0.01),
                Map.entry("Leadership", 0.015)
        );

        static final Map<String, Double> locationMultiplier = Map.of(
                "US", 1.00, "EU", 0.85, "UK", 0.90, "CA", 0.90, "Remote", 0.95, "MEA", 0.65, "APAC", 0.80
        );
    }

    static class Row {
        String role, level, location;
        double baseLow, baseHigh, demandIndex;
        List<String> skills;

        Row(String role, String level, double baseLow, double baseHigh, List<String> skills, String location, double demandIndex) {
            this.role = role; this.level = level; this.baseLow = baseLow; this.baseHigh = baseHigh;
            this.skills = skills; this.location = location; this.demandIndex = demandIndex;
        }
    }

    static final List<Row> BASELINES_2025 = List.of(
            new Row("Data Analyst",              "Mid",     65000,  95000,  List.of("SQL","Excel","Power BI"),          "US", 1.10),
            new Row("BI Analyst",                "Mid",     70000, 105000,  List.of("SQL","Power BI","DAX"),            "US", 1.08),
            new Row("Business Analyst",          "Mid",     70000, 110000,  List.of("SQL","Excel"),                     "US", 1.06),
            new Row("Product Analyst",           "Mid",     80000, 125000,  List.of("SQL","Experimentation"),            "US", 1.12),
            new Row("Analytics Engineer",        "Senior", 110000, 165000,  List.of("SQL","Python","dbt","Snowflake"),   "US", 1.18),
            new Row("Data Engineer",             "Senior", 115000, 175000,  List.of("SQL","Python","Spark"),             "US", 1.17),
            new Row("Data Scientist",            "Senior", 120000, 180000,  List.of("Python","Machine Learning"),         "US", 1.15),
            new Row("Machine Learning Engineer", "Senior", 140000, 210000,  List.of("Python","Machine Learning"),         "US", 1.20),
            new Row("BI Developer",              "Senior",  90000, 140000,  List.of("SQL","Power BI"),                   "US", 1.10),
            new Row("Marketing Analyst",         "Mid",     65000, 100000,  List.of("SQL","Excel"),                      "US", 1.07),
            new Row("AI Analyst",                "Mid",     85000, 135000,  List.of("SQL","Python"),                     "US", 1.13),
            new Row("Analytics Manager",         "Manager",130000, 200000,  List.of("SQL","Leadership"),                 "US", 1.12)
    );

    static int yearsBetween(int baseYear, int targetYear) {
        return Math.max(0, targetYear - baseYear);
    }
    static double applyCagr(double amount, double cagr, int years) {
        return amount * Math.pow(1.0 + cagr, years);
    }
    static double applyInflation(double amount, double infl, int years) {
        return amount * Math.pow(1.0 + infl, years);
    }
    static double skillsMultiplier(List<String> skills) {
        double mult = 1.0;
        for (String s : skills) mult *= (1.0 + CFG.skillPremium.getOrDefault(s, 0.0));
        return mult;
    }
    static double round2(double x) { return Math.round(x * 100.0) / 100.0; }

    static class Pred {
        Row r;
        double low, high, mid;
        Pred(Row r, double low, double high, double mid){ this.r = r; this.low = low; this.high = high; this.mid = mid; }
    }

    static Pred predict(Row r) {
        int yrs = yearsBetween(CFG.baseYear, CFG.targetYear);
        double cagr = CFG.cagrByLevel.getOrDefault(r.level, 0.06);

        double low = applyCagr(r.baseLow, cagr, yrs);
        double high = applyCagr(r.baseHigh, cagr, yrs);

        low = applyInflation(low, CFG.inflationRate, yrs);
        high = applyInflation(high, CFG.inflationRate, yrs);

        double sm = skillsMultiplier(r.skills);
        low *= sm; high *= sm;

        double lm = CFG.locationMultiplier.getOrDefault(r.location, 0.90);
        low *= lm * r.demandIndex;
        high *= lm * r.demandIndex;

        double mid = (low + high) / 2.0;
        return new Pred(r, low, high, mid);
    }

    static String esc(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }

    static void writeJson(List<Pred> preds) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < preds.size(); i++) {
            Pred p = preds.get(i);
            sb.append("  {")
              .append("\"role\":\"").append(esc(p.r.role)).append("\",")
              .append("\"level\":\"").append(esc(p.r.level)).append("\",")
              .append("\"base_year\":").append(CFG.baseYear).append(",")
              .append("\"target_year\":").append(CFG.targetYear).append(",")
              .append("\"location\":\"").append(esc(p.r.location)).append("\",")
              .append("\"skills\":").append(p.r.skills.toString().replace("=",":")).append(",")
              .append("\"demand_index\":").append(p.r.demandIndex).append(",")
              .append("\"base_low\":").append(p.r.baseLow).append(",")
              .append("\"base_high\":").append(p.r.baseHigh).append(",")
              .append("\"projected_low\":").append(round2(p.low)).append(",")
              .append("\"projected_high\":").append(round2(p.high)).append(",")
              .append("\"projected_mid\":").append(round2(p.mid))
              .append("}");
            if (i < preds.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]\n");
        Files.writeString(Paths.get(OUT_JSON), sb.toString(), StandardCharsets.UTF_8);
    }

    static void drawBarChart(List<Pred> preds, String title, String outFile) throws IOException {
        int W = 1400, H = 700;
        int padL = 90, padR = 40, padT = 70, padB = 220;
        int chartW = W - padL - padR;
        int chartH = H - padT - padB;

        double maxV = preds.stream().mapToDouble(p -> p.mid).max().orElse(1) * 1.1;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString(title, 40, 40);

        // axes
        g.drawLine(padL, padT, padL, padT + chartH);
        g.drawLine(padL, padT + chartH, padL + chartW, padT + chartH);

        int n = preds.size();
        int gap = 10;
        int barW = Math.max(10, (chartW - gap * (n - 1)) / n);

        g.setColor(new Color(76, 120, 168));
        for (int i = 0; i < n; i++) {
            double v = preds.get(i).mid;
            int h = (int) Math.round((v / maxV) * chartH);
            int x = padL + i * (barW + gap);
            int y = padT + (chartH - h);
            g.fillRect(x, y, barW, h);
        }

        // labels (rotated)
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        for (int i = 0; i < n; i++) {
            String label = preds.get(i).r.role;
            int x = padL + i * (barW + gap) + barW / 2;
            int y = padT + chartH + 15;
            g.translate(x, y);
            g.rotate(-Math.PI / 4);
            g.drawString(label, 0, 0);
            g.rotate(Math.PI / 4);
            g.translate(-x, -y);
        }

        g.dispose();
        ImageIO.write(img, "png", new File(outFile));
    }

    static void drawRangeChart(List<Pred> preds, String title, String outFile) throws IOException {
        int W = 1400, H = 700;
        int padL = 90, padR = 40, padT = 70, padB = 220;
        int chartW = W - padL - padR;
        int chartH = H - padT - padB;

        double maxV = preds.stream().mapToDouble(p -> p.high).max().orElse(1) * 1.1;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString(title, 40, 40);

        // axes
        g.drawLine(padL, padT, padL, padT + chartH);
        g.drawLine(padL, padT + chartH, padL + chartW, padT + chartH);

        int n = preds.size();
        int gap = 10;
        int slotW = Math.max(10, (chartW - gap * (n - 1)) / n);

        Color orange = new Color(245, 133, 24);
        g.setColor(orange);
        g.setStroke(new BasicStroke(4));

        for (int i = 0; i < n; i++) {
            Pred p = preds.get(i);
            int x = padL + i * (slotW + gap) + slotW / 2;

            int yHigh = padT + (int) Math.round(chartH - (p.high / maxV) * chartH);
            int yLow  = padT + (int) Math.round(chartH - (p.low  / maxV) * chartH);

            g.drawLine(x, yHigh, x, yLow);
            g.drawLine(x - 10, yHigh, x + 10, yHigh);
            g.drawLine(x - 10, yLow,  x + 10, yLow);
        }

        // labels (rotated)
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        for (int i = 0; i < n; i++) {
            String label = preds.get(i).r.role;
            int x = padL + i * (slotW + gap) + slotW / 2;
            int y = padT + chartH + 15;
            g.translate(x, y);
            g.rotate(-Math.PI / 4);
            g.drawString(label, 0, 0);
            g.rotate(Math.PI / 4);
            g.translate(-x, -y);
        }

        g.dispose();
        ImageIO.write(img, "png", new File(outFile));
    }

    public static void main(String[] args) throws Exception {
        List<Pred> preds = new ArrayList<>();
        for (Row r : BASELINES_2025) preds.add(predict(r));

        writeJson(preds);
        drawBarChart(preds, "Projected 2026 Mid Salary by Role", OUT_BAR);
        drawRangeChart(preds, "Projected 2026 Salary Range (Low–High) by Role", OUT_RANGE);

        System.out.println("✅ Wrote " + OUT_JSON);
        System.out.println("✅ Saved " + OUT_BAR);
        System.out.println("✅ Saved " + OUT_RANGE);
    }
}
