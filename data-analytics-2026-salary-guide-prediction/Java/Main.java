package com.salaryguide;

import org.jsoup.Jsoup;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;

public class Main {

    // ============================================================
    // Data Analytics Salary Guide (US, 2026) — Live O*NET/BLS + Chart
    // ============================================================
    // LEGIT DATA SOURCE (USED LIVE AT RUNTIME)
    // ============================================================
    // O*NET wage pages provide "Save Table: CSV".
    // We fetch the national CSV (United States row) for each O*NET code:
    //   https://www.onetonline.org/link/localwagestable/{code}/LocalWages_{code}_US.csv?fmt=csv
    //
    // O*NET wage pages show they use BLS wage data (BLS-backed source).
    // Example page:
    //   https://www.onetonline.org/link/localwages/15-2051.00
    //
    // We use annual percentiles:
    // - Annual Low (10%)    -> Entry proxy
    // - Annual Median (50%) -> Mid proxy
    // - Annual High (90%)   -> Senior proxy
    // ============================================================

    private static String onetPageUrl(String code) {
        return "https://www.onetonline.org/link/localwages/" + code;
    }

    private static String onetCsvUrl(String code) {
        return "https://www.onetonline.org/link/localwagestable/" + code + "/LocalWages_" + code + "_US.csv?fmt=csv";
    }

    // ------------------------------------------------------------
    // Where the data you took is defined (role -> official O*NET code)
    // ------------------------------------------------------------
    private static final Map<String, String> ROLE_TO_ONET = new LinkedHashMap<>() {{
        put("Data Analyst (proxy: Operations Research Analysts)", "15-2031.00");
        put("Business Intelligence Analyst", "15-2051.01");
        put("Data Scientist", "15-2051.00");
        put("Data Engineer (proxy: Software Developers)", "15-1252.00");
    }};

    // ------------------------------------------------------------
    // Model parameters (edit to match your guide assumptions)
    // ------------------------------------------------------------
    private static final double INFLATION = 0.03; // 3%
    private static final double DEMAND = 0.12;    // 12%

    // Skills premiums (stacked) -> sum is applied as one multiplier
    private static final Map<String, Double> SKILLS_PREMIUMS = new LinkedHashMap<>() {{
        put("SQL", 0.04);
        put("Python", 0.05);
        put("PowerBI/Tableau", 0.04);
        put("Cloud (AWS/Azure/GCP)", 0.05);
        put("ML", 0.06);
    }};

    // Optional per-role demand bonus
    private static final Map<String, Double> ROLE_DEMAND_BONUS = new HashMap<>() {{
        put("Data Analyst (proxy: Operations Research Analysts)", 0.00);
        put("Business Intelligence Analyst", 0.01);
        put("Data Scientist", 0.02);
        put("Data Engineer (proxy: Software Developers)", 0.02);
    }};

    // ===== Data structure =====
    static class WageRow {
        String role;
        String code;
        String sourcePage;
        String sourceCsv;

        double p10;
        double p50;
        double p90;

        double entry2026;
        double mid2026;
        double senior2026;
    }

    // ===== Salary guide pipeline =====
    private static double inflationAdjustment(double salary) {
        return salary * (1.0 + INFLATION);
    }

    private static double demandAdjustment(double salary, double roleBonus) {
        return salary * (1.0 + DEMAND + roleBonus);
    }

    private static double skillsAdjustment(double salary) {
        double skillSum = SKILLS_PREMIUMS.values().stream().mapToDouble(Double::doubleValue).sum();
        return salary * (1.0 + skillSum);
    }

    private static double predict2026(double baseSalary, double roleBonus) {
        double s = inflationAdjustment(baseSalary);
        s = demandAdjustment(s, roleBonus);
        s = skillsAdjustment(s);
        return Math.round(s);
    }

    // ===== CSV parsing (robust for quotes) =====
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                // escaped quote
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static double moneyToDouble(String s) {
        String cleaned = s.replace("$", "").replace(",", "").trim();
        return Double.parseDouble(cleaned);
    }

    private static WageRow fetchOnetWages(String role, String code) throws IOException {
        String csvUrl = onetCsvUrl(code);
        String pageUrl = onetPageUrl(code);

        // Fetch CSV (legit export)
        String csvText = Jsoup.connect(csvUrl)
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0")
                .timeout(30000)
                .execute()
                .body();

        String[] rawLines = csvText.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            String t = l.trim();
            if (!t.isEmpty()) lines.add(t);
        }
        if (lines.size() < 2) throw new IOException("CSV looks empty: " + csvUrl);

        List<String> header = parseCsvLine(lines.get(0));
        int idxLoc = indexOfHeader(header, "location");
        int idxP10 = indexOfContains(header, "annual low");
        int idxP50 = indexOfContains(header, "annual median");
        int idxP90 = indexOfContains(header, "annual high");

        if (idxLoc < 0 || idxP10 < 0 || idxP50 < 0 || idxP90 < 0) {
            throw new IOException("CSV header not as expected for " + code + " header=" + header);
        }

        List<String> usRow = null;
        for (int i = 1; i < lines.size(); i++) {
            List<String> cols = parseCsvLine(lines.get(i));
            if (cols.size() > idxLoc && "United States".equals(cols.get(idxLoc).trim())) {
                usRow = cols;
                break;
            }
        }
        if (usRow == null) throw new IOException("No 'United States' row found for " + code);

        double p10 = moneyToDouble(usRow.get(idxP10));
        double p50 = moneyToDouble(usRow.get(idxP50));
        double p90 = moneyToDouble(usRow.get(idxP90));

        if (p10 <= 0 || p50 <= 0 || p90 <= 0) throw new IOException("Invalid wages parsed for " + code);

        WageRow r = new WageRow();
        r.role = role;
        r.code = code;
        r.sourcePage = pageUrl;
        r.sourceCsv = csvUrl;
        r.p10 = p10;
        r.p50 = p50;
        r.p90 = p90;
        return r;
    }

    private static int indexOfHeader(List<String> header, String exactLower) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).trim().equalsIgnoreCase(exactLower)) return i;
        }
        return -1;
    }

    private static int indexOfContains(List<String> header, String containsLower) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).toLowerCase(Locale.ROOT).contains(containsLower)) return i;
        }
        return -1;
    }

    // ===== Reports + Chart =====
    private static void writeCSV(List<WageRow> rows) throws IOException {
        try (FileWriter csv = new FileWriter("salary_data.csv", StandardCharsets.UTF_8)) {
            csv.write("Role,O*NET Code,Source Page,Source CSV,Entry Base (p10),Mid Base (p50),Senior Base (p90),Entry 2026,Mid 2026,Senior 2026\n");
            for (WageRow r : rows) {
                csv.write(String.format(Locale.US,
                        "\"%s\",%s,\"%s\",\"%s\",%.0f,%.0f,%.0f,%.0f,%.0f,%.0f\n",
                        r.role, r.code, r.sourcePage, r.sourceCsv,
                        r.p10, r.p50, r.p90,
                        r.entry2026, r.mid2026, r.senior2026
                ));
            }
        }
        System.out.println("📊 Saved CSV: salary_data.csv");
    }

    private static void writeHTML(List<WageRow> rows) throws IOException {
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        double skillSum = SKILLS_PREMIUMS.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalAdj = ((1 + INFLATION) * (1 + DEMAND) * (1 + skillSum) - 1) * 100;

        try (FileWriter html = new FileWriter("salary_report.html", StandardCharsets.UTF_8)) {
            html.write("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"/>");
            html.write("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>");
            html.write("<title>Data Analytics Salary Guide 2026</title>");
            html.write("<style>");
            html.write("body{font-family:Arial,sans-serif;margin:40px;background:#f5f5f5;}");
            html.write(".container{max-width:1100px;margin:0 auto;background:#fff;padding:30px;border-radius:10px;box-shadow:0 2px 10px rgba(0,0,0,.1);}");
            html.write("h1{color:#2c3e50;border-bottom:3px solid #3498db;padding-bottom:10px;}");
            html.write("table{width:100%;border-collapse:collapse;margin:18px 0;}");
            html.write("th,td{padding:10px;text-align:left;border-bottom:1px solid #ddd;}");
            html.write("th{background:#3498db;color:#fff;}");
            html.write("tr:hover{background:#f9f9f9;}");
            html.write(".note{background:#e8f4fc;padding:14px;border-radius:6px;margin:16px 0;}");
            html.write(".currency{font-family:monospace;font-weight:700;color:#27ae60;}");
            html.write("</style></head><body><div class=\"container\">");

            html.write("<h1>📊 Data Analytics Salary Guide 2026 (O*NET / BLS)</h1>");
            html.write("<div class=\"note\">");
            html.write("<b>Legit source:</b> O*NET “National Wages” CSV export (United States row). ");
            html.write("<br/><b>Model params:</b> ");
            html.write(String.format(Locale.US, "Inflation %.1f%% • Demand %.1f%% • Skills %.1f%% • Total %.1f%%",
                    INFLATION * 100, DEMAND * 100, skillSum * 100, totalAdj));
            html.write("<br/>✅ PNG chart generated: <b>salary_chart.png</b>");
            html.write("</div>");

            html.write("<h2>Base (p10/p50/p90) + 2026 Predictions</h2>");
            html.write("<table><thead><tr>");
            html.write("<th>Role</th><th>O*NET Code</th><th>Entry Base (p10)</th><th>Mid Base (p50)</th><th>Senior Base (p90)</th>");
            html.write("<th>Entry 2026</th><th>Mid 2026</th><th>Senior 2026</th><th>Sources</th>");
            html.write("</tr></thead><tbody>");

            for (WageRow r : rows) {
                html.write("<tr>");
                html.write("<td><strong>" + escape(r.role) + "</strong></td>");
                html.write("<td>" + escape(r.code) + "</td>");
                html.write("<td class=\"currency\">" + currency.format(r.p10) + "</td>");
                html.write("<td class=\"currency\">" + currency.format(r.p50) + "</td>");
                html.write("<td class=\"currency\">" + currency.format(r.p90) + "</td>");
                html.write("<td class=\"currency\">" + currency.format(r.entry2026) + "</td>");
                html.write("<td class=\"currency\">" + currency.format(r.mid2026) + "</td>");
                html.write("<td class=\"currency\">" + currency.format(r.senior2026) + "</td>");
                html.write("<td><a href=\"" + r.sourcePage + "\" target=\"_blank\">Page</a> · " +
                        "<a href=\"" + r.sourceCsv + "\" target=\"_blank\">CSV</a></td>");
                html.write("</tr>");
            }

            html.write("</tbody></table>");
            html.write("<div style=\"margin-top:22px;color:#666;font-size:13px;border-top:1px solid #ddd;padding-top:12px;\">");
            html.write("Generated on: " + new Date());
            html.write("</div>");

            html.write("</div></body></html>");
        }

        System.out.println("✅ Generated HTML: salary_report.html");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void generateChart(List<WageRow> rows) throws IOException {
        List<String> labels = new ArrayList<>();
        List<Double> entry = new ArrayList<>();
        List<Double> mid = new ArrayList<>();
        List<Double> senior = new ArrayList<>();

        for (WageRow r : rows) {
            labels.add(r.role.replaceAll("\\s*\\(.*?\\)\\s*", "").trim());
            entry.add(r.entry2026);
            mid.add(r.mid2026);
            senior.add(r.senior2026);
        }

        CategoryChart chart = new CategoryChartBuilder()
                .width(1400).height(650)
                .title("Data Analytics Salaries 2026 — O*NET/BLS Base + Adjustments")
                .xAxisTitle("Role").yAxisTitle("Salary (USD)")
                .build();

        chart.addSeries("Entry (p10 proxy)", labels, entry);
        chart.addSeries("Mid (p50 proxy)", labels, mid);
        chart.addSeries("Senior (p90 proxy)", labels, senior);

        BitmapEncoder.saveBitmap(chart, "salary_chart", BitmapEncoder.BitmapFormat.PNG);
        System.out.println("🖼️ Generated chart: salary_chart.png");
    }

    public static void main(String[] args) {
        System.out.println("📊 Data Analytics Salary Guide 2026 (LIVE O*NET/BLS + CHART)");
        System.out.println("=".repeat(70));

        double skillSum = SKILLS_PREMIUMS.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalAdjustment = ((1 + INFLATION) * (1 + DEMAND) * (1 + skillSum) - 1) * 100;

        System.out.println("\n=== MODEL PARAMETERS ===");
        System.out.printf(Locale.US, "Inflation: %.1f%%%n", INFLATION * 100);
        System.out.printf(Locale.US, "Demand:    %.1f%%%n", DEMAND * 100);
        System.out.printf(Locale.US, "Skills:    %.1f%%%n", skillSum * 100);
        System.out.printf(Locale.US, "Total:     %.1f%%%n", totalAdjustment);

        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);

        List<WageRow> rows = new ArrayList<>();
        List<Map<String, String>> errors = new ArrayList<>();

        System.out.println("\n=== DATA SOURCES USED (LIVE) ===");
        for (Map.Entry<String, String> e : ROLE_TO_ONET.entrySet()) {
            String role = e.getKey();
            String code = e.getValue();

            System.out.println("\n📡 Fetching: " + role + " -> " + code);
            System.out.println("   Page: " + onetPageUrl(code));
            System.out.println("   CSV : " + onetCsvUrl(code));

            try {
                WageRow r = fetchOnetWages(role, code);

                double roleBonus = ROLE_DEMAND_BONUS.getOrDefault(role, 0.0);

                r.entry2026 = predict2026(r.p10, roleBonus);
                r.mid2026 = predict2026(r.p50, roleBonus);
                r.senior2026 = predict2026(r.p90, roleBonus);

                rows.add(r);

                System.out.printf(Locale.US, "   ✓ Base p10=%.0f p50=%.0f p90=%.0f%n", r.p10, r.p50, r.p90);
                System.out.println("   -> Predicted 2026:");
                System.out.println("      Entry:  " + currency.format(r.p10) + " → " + currency.format(r.entry2026));
                System.out.println("      Mid:    " + currency.format(r.p50) + " → " + currency.format(r.mid2026));
                System.out.println("      Senior: " + currency.format(r.p90) + " → " + currency.format(r.senior2026));
            } catch (Exception ex) {
                System.out.println("   ❌ FAILED: " + ex.getMessage());
                Map<String, String> err = new LinkedHashMap<>();
                err.put("role", role);
                err.put("code", code);
                err.put("pageUrl", onetPageUrl(code));
                err.put("csvUrl", onetCsvUrl(code));
                err.put("error", ex.getMessage());
                errors.add(err);
            }
        }

        if (rows.isEmpty()) {
            System.out.println("\n❌ No roles could be fetched.");
            return;
        }

        try {
            writeCSV(rows);
            writeHTML(rows);
            generateChart(rows);
        } catch (IOException ex) {
            System.out.println("❌ Output generation error: " + ex.getMessage());
        }

        if (!errors.isEmpty()) {
            try (FileWriter w = new FileWriter("errors.json", StandardCharsets.UTF_8)) {
                w.write(toJson(errors));
            } catch (IOException ignored) {}
            System.out.println("⚠️ Some roles failed. See: errors.json");
        }

        System.out.println("\n✅ Done. Files generated:");
        System.out.println(" - salary_report.html");
        System.out.println(" - salary_data.csv");
        System.out.println(" - salary_chart.png");
    }

    // Tiny JSON writer (no external deps)
    private static String toJson(List<Map<String, String>> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < errors.size(); i++) {
            Map<String, String> e = errors.get(i);
            sb.append("  {");
            int j = 0;
            for (var kv : e.entrySet()) {
                sb.append("\"").append(kv.getKey()).append("\": ");
                sb.append("\"").append(kv.getValue().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                if (++j < e.size()) sb.append(", ");
            }
            sb.append("}");
            if (i + 1 < errors.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }
}
