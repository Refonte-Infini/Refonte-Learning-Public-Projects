/*
 * Software Engineering Salary Prediction (LEGIT SOURCES) + CHART (PNG)
 * -------------------------------------------------------------------
 * Same pipeline style as your Java example:
 *  - fetch data (REAL)
 *  - optional BERT extraction stub (OFF by default)
 *  - apply inflation + skills premium + demand + geographic (+ CAGR-like)
 *  - print results
 *  - generate a chart PNG
 *
 * ✅ Legit data source:
 * We fetch wage percentiles from O*NET OnLine "National Wages" pages:
 *   https://www.onetonline.org/link/localwages/{O_NET_CODE}
 *
 * O*NET wage tables are BLS-backed (O*NET pages cite Bureau of Labor Statistics wage data).
 *
 * Career-level proxy:
 *  - Entry  = 10th percentile (p10)
 *  - Mid    = 50th percentile (median p50)
 *  - Senior = 90th percentile (p90)
 *
 * Output chart file:
 *  software_engineering_bls_onet_2026.png
 *
 * ---------------------------------------------------------------
 * BUILD (Maven):
 *  mvn -q -DskipTests package
 *
 * RUN:
 *  mvn -q exec:java -Dexec.mainClass=Main
 *  mvn -q exec:java -Dexec.mainClass=Main -Dexec.args="--year 2026 --inflation 0.03"
 */

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    // ============================================================
    // 1) WHERE THE DATA CAME FROM (LEGIT SOURCE MAPPING)
    // ============================================================
    // O*NET national wages page:
    //   https://www.onetonline.org/link/localwages/{code}
    //
    // Note: Government sources don't publish a separate "DevOps Engineer" occupation,
    // so we use transparent proxies to official O*NET occupations and print them.
    static final Map<String, String> ROLE_TO_ONET = new LinkedHashMap<>() {{
        put("Software Developer", "15-1252.00");
        put("Web Developer", "15-1254.00");
        put("Information Security Analyst", "15-1212.00");
        put("Data Scientist (proxy for ML Engineer)", "15-2051.00");

        // Proxies (clearly labeled)
        put("DevOps Engineer (proxy: Software Developers)", "15-1252.00");
        put("Cloud Engineer (proxy: Network & Computer Systems Admins)", "15-1244.00");
    }};

    static String onetWagesUrl(String code) {
        return "https://www.onetonline.org/link/localwages/" + code;
    }

    // ============================================================
    // 2) Demand / Geographic / Skills factors (replace with real ones)
    // ============================================================
    static final Map<String, Double> DEMAND_FACTOR = new HashMap<>() {{
        put("Software Developer", 0.10);
        put("Web Developer", 0.08);
        put("Information Security Analyst", 0.13);
        put("Data Scientist (proxy for ML Engineer)", 0.12);
        put("DevOps Engineer (proxy: Software Developers)", 0.12);
        put("Cloud Engineer (proxy: Network & Computer Systems Admins)", 0.11);
    }};

    static final Map<String, Double> GEOGRAPHIC_FACTOR = new HashMap<>() {{
        put("Software Developer", 0.05);
        put("Web Developer", 0.05);
        put("Information Security Analyst", 0.06);
        put("Data Scientist (proxy for ML Engineer)", 0.06);
        put("DevOps Engineer (proxy: Software Developers)", 0.06);
        put("Cloud Engineer (proxy: Network & Computer Systems Admins)", 0.05);
    }};

    static final Map<String, Double> SKILLS_PREMIUMS = new LinkedHashMap<>() {{
        put("AWS", 0.04);
        put("Kubernetes", 0.05);
        put("Terraform", 0.03);
        put("Security", 0.03);
        put("SystemDesign", 0.04);
        put("MachineLearning", 0.06);
    }};

    // ============================================================
    // 3) Fetch data from O*NET (REAL) instead of mock APIs
    // ============================================================
    // Parse "United States" row which contains annual percentiles:
    // Annual Low (10%), Annual Q L (25%), Annual Median (50%), Annual Q U (75%), Annual High (90%)
    static JSONObject fetchOnetPercentileWages(String onetCode) throws Exception {
        String url = onetWagesUrl(onetCode);

        // Jsoup fetch
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; SalaryPredictor/1.0)")
                .timeout(30000)
                .get();

        String text = doc.text().replaceAll("\\s+", " ").trim();

        // Find text chunk like:
        // "United States $79,850 $99,xxx $131,xxx $166,xxx $211,450"
        Pattern p = Pattern.compile("United States\\s+\\$[\\d,]+\\s+\\$[\\d,]+\\s+\\$[\\d,]+\\s+\\$[\\d,]+\\s+\\$[\\d,]+");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            throw new IllegalStateException("Could not locate United States wage row for " + onetCode + " at " + url);
        }

        String row = m.group(0);
        Matcher dollarsM = Pattern.compile("\\$[\\d,]+").matcher(row);
        List<String> dollars = new ArrayList<>();
        while (dollarsM.find()) dollars.add(dollarsM.group(0));

        if (dollars.size() < 5) {
            throw new IllegalStateException("Unexpected wage row format for " + onetCode + ": " + row);
        }

        double p10 = moneyToNumber(dollars.get(0));
        double p50 = moneyToNumber(dollars.get(2));
        double p90 = moneyToNumber(dollars.get(4));

        JSONObject out = new JSONObject();
        out.put("source_url", url);
        out.put("p10", p10);
        out.put("p50", p50);
        out.put("p90", p90);
        return out;
    }

    static double moneyToNumber(String s) {
        return Double.parseDouble(s.replace("$", "").replace(",", "").trim());
    }

    // ============================================================
    // 4) (Optional) BERT Extraction Stub — same interface, OFF by default
    // ============================================================
    static JSONObject extractSalaryDetails(String jobDescription) {
        // Stub only (like your mock BERT in Java example)
        JSONObject bertResult = new JSONObject();
        if (jobDescription.toLowerCase().contains("software developer")) {
            bertResult.put("Role", "Software Developer");
            bertResult.put("Level", "Entry-Level");
            bertResult.put("SalaryRange", "$80,000–$120,000");
        }
        return bertResult;
    }

    // ============================================================
    // 5) Models (same as your example)
    // ============================================================
    static double calculateCAGR(double presentValue, double futureValue, int years) {
        return Math.pow(futureValue / presentValue, 1.0 / years) - 1.0;
    }

    static double adjustForInflation(double salary, double inflationRate) {
        return salary * (1 + inflationRate);
    }

    static double adjustForSkillsPremium(double baseSalary, Map<String, Double> skillFactors) {
        double totalPremium = skillFactors.values().stream().mapToDouble(Double::doubleValue).sum();
        return baseSalary * (1 + totalPremium);
    }

    static double adjustForDemandAndGeography(double baseSalary, double demandFactor, double geographicFactor) {
        return baseSalary * (1 + demandFactor + geographicFactor);
    }

    // ============================================================
    // 6) Chart
    // ============================================================
    static void renderChart(List<JSONObject> results, int year, String outPath) throws Exception {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (JSONObject r : results) {
            String role = r.getString("Role");
            dataset.addValue(r.getDouble("Entry-Level " + year), "Entry (p10)", role);
            dataset.addValue(r.getDouble("Mid-Level " + year), "Mid (p50)", role);
            dataset.addValue(r.getDouble("Senior-Level " + year), "Senior (p90)", role);
        }

        String title = "Software Engineering Salaries (" + year + ") — O*NET (BLS-backed) + Adjustments";
        JFreeChart chart = ChartFactory.createBarChart(
                title,
                "Role",
                "Salary (USD)",
                dataset
        );

        ChartUtils.saveChartAsPNG(new File(outPath), chart, 1600, 700);
        System.out.println("\nChart saved to: " + outPath);
    }

    // ============================================================
    // 7) CLI args
    // ============================================================
    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                String val = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "true";
                out.put(key, val);
            }
        }
        return out;
    }

    // ============================================================
    // 8) Main
    // ============================================================
    public static void main(String[] args) throws Exception {
        Map<String, String> a = parseArgs(args);

        int year = Integer.parseInt(a.getOrDefault("year", "2026"));
        double inflationRate = Double.parseDouble(a.getOrDefault("inflation", "0.03"));
        String outChart = a.getOrDefault("out", "software_engineering_bls_onet_2026.png");
        boolean useBert = Boolean.parseBoolean(a.getOrDefault("useBert", "false"));

        // Optional BERT stub
        if (useBert) {
            List<String> jobDescriptions = Arrays.asList(
                    "Hiring an entry-level Software Developer. Salary range $80,000–$120,000.",
                    "Hiring a senior Information Security Analyst. Salary up to $190,000."
            );
            System.out.println("BERT Extracted Data (stub):");
            for (String d : jobDescriptions) {
                System.out.println(extractSalaryDetails(d));
            }
        }

        // 1) Pull legit wages per role from O*NET (BLS-backed)
        System.out.println("\n=== LEGIT SOURCES USED (O*NET National Wages pages; BLS-backed wage data) ===");
        JSONArray baseRows = new JSONArray();

        for (Map.Entry<String, String> e : ROLE_TO_ONET.entrySet()) {
            String role = e.getKey();
            String code = e.getValue();
            JSONObject wages = fetchOnetPercentileWages(code);

            System.out.println("- " + role + " -> O*NET " + code + " -> " + wages.getString("source_url"));

            JSONObject row = new JSONObject();
            row.put("Role", role);
            row.put("O*NET Code", code);
            row.put("Source URL", wages.getString("source_url"));
            row.put("Entry-Level Base (p10)", wages.getDouble("p10"));
            row.put("Mid-Level Base (p50)", wages.getDouble("p50"));
            row.put("Senior-Level Base (p90)", wages.getDouble("p90"));
            baseRows.put(row);
        }

        // Show "where data is" explicitly
        System.out.println("\n=== BASE WAGES PULLED (from the URLs above) ===");
        for (int i = 0; i < baseRows.length(); i++) {
            JSONObject r = baseRows.getJSONObject(i);
            System.out.println(r.getString("Role") + " | "
                    + "p10=" + String.format(Locale.US, "%,.0f", r.getDouble("Entry-Level Base (p10)")) + " | "
                    + "p50=" + String.format(Locale.US, "%,.0f", r.getDouble("Mid-Level Base (p50)")) + " | "
                    + "p90=" + String.format(Locale.US, "%,.0f", r.getDouble("Senior-Level Base (p90)")) + " | "
                    + r.getString("Source URL"));
        }

        // 2) Apply pipeline (CAGR-like + inflation + skills + demand+geo)
        List<JSONObject> results = new ArrayList<>();

        for (int i = 0; i < baseRows.length(); i++) {
            JSONObject r = baseRows.getJSONObject(i);
            String role = r.getString("Role");

            double entryBase = r.getDouble("Entry-Level Base (p10)");
            double midBase = r.getDouble("Mid-Level Base (p50)");
            double seniorBase = r.getDouble("Senior-Level Base (p90)");

            double demand = DEMAND_FACTOR.getOrDefault(role, 0.0);
            double geo = GEOGRAPHIC_FACTOR.getOrDefault(role, 0.0);

            // CAGR-like (synthetic, same style as your example)
            double entryCAGR = calculateCAGR(entryBase * 0.95, entryBase, 1);
            double midCAGR = calculateCAGR(midBase * 0.90, midBase, 1);
            double seniorCAGR = calculateCAGR(seniorBase * 0.85, seniorBase, 1);

            double entryInflated = adjustForInflation(entryBase, inflationRate + entryCAGR);
            double midInflated = adjustForInflation(midBase, inflationRate + midCAGR);
            double seniorInflated = adjustForInflation(seniorBase, inflationRate + seniorCAGR);

            double entrySkills = adjustForSkillsPremium(entryInflated, SKILLS_PREMIUMS);
            double midSkills = adjustForSkillsPremium(midInflated, SKILLS_PREMIUMS);
            double seniorSkills = adjustForSkillsPremium(seniorInflated, SKILLS_PREMIUMS);

            double entryFinal = adjustForDemandAndGeography(entrySkills, demand, geo);
            double midFinal = adjustForDemandAndGeography(midSkills, demand, geo);
            double seniorFinal = adjustForDemandAndGeography(seniorSkills, demand, geo);

            JSONObject out = new JSONObject();
            out.put("Role", role);
            out.put("Source URL", r.getString("Source URL"));
            out.put("Entry-Level " + year, entryFinal);
            out.put("Mid-Level " + year, midFinal);
            out.put("Senior-Level " + year, seniorFinal);

            results.add(out);
        }

        // Display results
        System.out.println("\n=== PREDICTED SALARIES for " + year + " (after inflation + skills + demand + geo) ===");
        for (JSONObject r : results) {
            System.out.println(r.getString("Role") + " | "
                    + "Entry=" + String.format(Locale.US, "%,.2f", r.getDouble("Entry-Level " + year)) + " | "
                    + "Mid=" + String.format(Locale.US, "%,.2f", r.getDouble("Mid-Level " + year)) + " | "
                    + "Senior=" + String.format(Locale.US, "%,.2f", r.getDouble("Senior-Level " + year)));
        }

        // Chart
        renderChart(results, year, outChart);
    }
}
