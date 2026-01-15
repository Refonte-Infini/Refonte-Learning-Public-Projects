import java.util.*;

public class Main {

    // Baselines (USD)
    static final Map<String, Map<String, int[]>> BASELINES_2024 = new HashMap<>();
    static {
        putRole("cybersecurity_engineer", new int[]{90000,120000}, new int[]{120000,160000}, new int[]{160000,210000});
        putRole("cloud_security_engineer", new int[]{105000,135000}, new int[]{135000,175000}, new int[]{175000,230000});
        putRole("devsecops_engineer", new int[]{100000,130000}, new int[]{130000,170000}, new int[]{170000,225000});
        putRole("appsec_engineer", new int[]{100000,135000}, new int[]{135000,180000}, new int[]{180000,240000});
        putRole("soc_analyst", new int[]{70000,95000}, new int[]{95000,125000}, new int[]{125000,160000});
        putRole("incident_response_dfir", new int[]{95000,125000}, new int[]{125000,165000}, new int[]{165000,220000});
        putRole("threat_hunter", new int[]{100000,130000}, new int[]{130000,175000}, new int[]{175000,230000});
        putRole("penetration_tester", new int[]{85000,115000}, new int[]{115000,155000}, new int[]{155000,210000});
        putRole("iam_engineer", new int[]{90000,120000}, new int[]{120000,160000}, new int[]{160000,210000});
        putRole("security_architect", new int[]{135000,175000}, new int[]{175000,220000}, new int[]{220000,280000});
        putRole("grc_analyst", new int[]{80000,105000}, new int[]{105000,140000}, new int[]{140000,185000});
    }

    static final Map<String, Double> CAGR_BY_ROLE = new HashMap<>();
    static {
        CAGR_BY_ROLE.put("default", 0.05);
        CAGR_BY_ROLE.put("cloud_security_engineer", 0.07);
        CAGR_BY_ROLE.put("devsecops_engineer", 0.07);
        CAGR_BY_ROLE.put("appsec_engineer", 0.06);
        CAGR_BY_ROLE.put("security_architect", 0.06);
    }

    static final double INFLATION_2024_TO_2026 = 1.07;

    static final Map<String, Double> GEO_MULTIPLIER = new HashMap<>();
    static {
        GEO_MULTIPLIER.put("CA", 1.18);
        GEO_MULTIPLIER.put("NY", 1.15);
        GEO_MULTIPLIER.put("WA", 1.12);
        GEO_MULTIPLIER.put("MA", 1.10);
        GEO_MULTIPLIER.put("DC", 1.12);
        GEO_MULTIPLIER.put("VA", 1.07);
        GEO_MULTIPLIER.put("TX", 1.03);
        GEO_MULTIPLIER.put("FL", 1.00);
        GEO_MULTIPLIER.put("IL", 1.02);
        GEO_MULTIPLIER.put("CO", 1.05);
        GEO_MULTIPLIER.put("GA", 0.98);
        GEO_MULTIPLIER.put("NC", 0.98);
        GEO_MULTIPLIER.put("AZ", 0.97);
        GEO_MULTIPLIER.put("OH", 0.95);
        GEO_MULTIPLIER.put("PA", 0.97);
        GEO_MULTIPLIER.put("REMOTE", 1.00);
        GEO_MULTIPLIER.put("DEFAULT", 1.00);
    }

    static final Map<String, Double> SKILL_PREMIUM = new HashMap<>();
    static {
        // cloud & platform security
        SKILL_PREMIUM.put("aws_security", 0.05);
        SKILL_PREMIUM.put("azure_security", 0.05);
        SKILL_PREMIUM.put("gcp_security", 0.05);
        SKILL_PREMIUM.put("kubernetes", 0.04);
        SKILL_PREMIUM.put("terraform", 0.03);
        SKILL_PREMIUM.put("containers", 0.03);
        SKILL_PREMIUM.put("cnapp", 0.04);
        SKILL_PREMIUM.put("cspm", 0.03);

        // engineering
        SKILL_PREMIUM.put("zero_trust", 0.04);
        SKILL_PREMIUM.put("iam", 0.03);
        SKILL_PREMIUM.put("okta", 0.02);
        SKILL_PREMIUM.put("entra_id", 0.02);
        SKILL_PREMIUM.put("sso_saml_oidc", 0.02);
        SKILL_PREMIUM.put("siem", 0.03);
        SKILL_PREMIUM.put("soar", 0.03);
        SKILL_PREMIUM.put("edr", 0.02);

        // DFIR/threat
        SKILL_PREMIUM.put("dfir", 0.05);
        SKILL_PREMIUM.put("incident_response", 0.04);
        SKILL_PREMIUM.put("threat_hunting", 0.04);
        SKILL_PREMIUM.put("malware_analysis", 0.04);
        SKILL_PREMIUM.put("reverse_engineering", 0.04);

        // AppSec
        SKILL_PREMIUM.put("secure_sdlc", 0.03);
        SKILL_PREMIUM.put("sast_dast", 0.03);
        SKILL_PREMIUM.put("threat_modeling", 0.03);

        // certs
        SKILL_PREMIUM.put("oscp", 0.06);
        SKILL_PREMIUM.put("gcih", 0.05);
        SKILL_PREMIUM.put("gcfa", 0.05);
        SKILL_PREMIUM.put("gpen", 0.05);
        SKILL_PREMIUM.put("cissp", 0.05);
        SKILL_PREMIUM.put("ccsp", 0.05);
        SKILL_PREMIUM.put("security_plus", 0.02);
    }

    static final Map<String, String> ROLE_ALIASES = new HashMap<>();
    static {
        ROLE_ALIASES.put("security engineer", "cybersecurity_engineer");
        ROLE_ALIASES.put("cybersecurity engineer", "cybersecurity_engineer");
        ROLE_ALIASES.put("cloud security engineer", "cloud_security_engineer");
        ROLE_ALIASES.put("devsecops engineer", "devsecops_engineer");
        ROLE_ALIASES.put("application security engineer", "appsec_engineer");
        ROLE_ALIASES.put("appsec engineer", "appsec_engineer");
        ROLE_ALIASES.put("soc analyst", "soc_analyst");
        ROLE_ALIASES.put("incident response", "incident_response_dfir");
        ROLE_ALIASES.put("dfir", "incident_response_dfir");
        ROLE_ALIASES.put("threat hunter", "threat_hunter");
        ROLE_ALIASES.put("penetration tester", "penetration_tester");
        ROLE_ALIASES.put("pen tester", "penetration_tester");
        ROLE_ALIASES.put("red team", "penetration_tester");
        ROLE_ALIASES.put("iam engineer", "iam_engineer");
        ROLE_ALIASES.put("security architect", "security_architect");
        ROLE_ALIASES.put("grc analyst", "grc_analyst");
    }

    static final Map<String, String> LEVEL_ALIASES = new HashMap<>();
    static {
        LEVEL_ALIASES.put("junior", "entry");
        LEVEL_ALIASES.put("entry", "entry");
        LEVEL_ALIASES.put("entry-level", "entry");
        LEVEL_ALIASES.put("mid", "mid");
        LEVEL_ALIASES.put("mid-level", "mid");
        LEVEL_ALIASES.put("intermediate", "mid");
        LEVEL_ALIASES.put("senior", "senior");
        LEVEL_ALIASES.put("lead", "senior");
        LEVEL_ALIASES.put("staff", "senior");
        LEVEL_ALIASES.put("principal", "senior");
    }

    static void putRole(String roleKey, int[] entry, int[] mid, int[] senior) {
        Map<String, int[]> m = new HashMap<>();
        m.put("entry", entry);
        m.put("mid", mid);
        m.put("senior", senior);
        BASELINES_2024.put(roleKey, m);
    }

    static String normalizeRole(String role) {
        String r = (role == null ? "" : role.trim().toLowerCase());
        if (ROLE_ALIASES.containsKey(r)) return ROLE_ALIASES.get(r);
        for (String k : ROLE_ALIASES.keySet()) {
            if (r.contains(k)) return ROLE_ALIASES.get(k);
        }
        String keyish = r.replaceAll("\\s+", "_");
        if (BASELINES_2024.containsKey(keyish)) return keyish;
        return "cybersecurity_engineer";
    }

    static String normalizeLevel(String level) {
        String l = (level == null ? "" : level.trim().toLowerCase());
        if (LEVEL_ALIASES.containsKey(l)) return LEVEL_ALIASES.get(l);
        for (String k : LEVEL_ALIASES.keySet()) {
            if (l.contains(k)) return LEVEL_ALIASES.get(k);
        }
        return "mid";
    }

    static double[] applyCagr(double low, double high, String roleKey, int years) {
        double cagr = CAGR_BY_ROLE.getOrDefault(roleKey, CAGR_BY_ROLE.get("default"));
        double factor = Math.pow(1.0 + cagr, years);
        return new double[]{low * factor, high * factor};
    }

    static double[] applyInflation(double low, double high) {
        return new double[]{low * INFLATION_2024_TO_2026, high * INFLATION_2024_TO_2026};
    }

    static double skillsMultiplier(List<String> skills) {
        double total = 0.0;
        for (String s : skills) total += SKILL_PREMIUM.getOrDefault(s, 0.0);
        total = Math.min(total, 0.25);
        return 1.0 + (0.85 * total);
    }

    static double geoMultiplier(String state) {
        String key = (state == null ? "" : state.trim().toUpperCase());
        return GEO_MULTIPLIER.getOrDefault(key, GEO_MULTIPLIER.get("DEFAULT"));
    }

    static double regressionAdjustment(String roleKey, String level, double yearsExp) {
        double target = 4.0;
        if ("entry".equals(level)) target = 1.0;
        if ("mid".equals(level)) target = 4.0;
        if ("senior".equals(level)) target = 8.0;

        double delta = yearsExp - target;
        double leverage = 0.008;
        if (Arrays.asList("security_architect", "cloud_security_engineer", "devsecops_engineer").contains(roleKey)) {
            leverage = 0.010;
        }
        double adj = delta * leverage;
        double clipped = Math.max(-0.05, Math.min(0.08, adj));
        return 1.0 + clipped;
    }

    static String money(double x) {
        return String.format("$%,.0f", x);
    }

    static Map<String, Object> predict2026(String role, String level, double yearsExp, String state, List<String> skills) {
        String roleKey = normalizeRole(role);
        String lvl = normalizeLevel(level);

        int[] base = BASELINES_2024.getOrDefault(roleKey, BASELINES_2024.get("cybersecurity_engineer"))
                                  .getOrDefault(lvl, BASELINES_2024.get("cybersecurity_engineer").get("mid"));

        double baseLow = base[0], baseHigh = base[1];

        double[] cagr = applyCagr(baseLow, baseHigh, roleKey, 2);
        double[] infl = applyInflation(cagr[0], cagr[1]);

        double sMult = skillsMultiplier(skills);
        double gMult = geoMultiplier(state);
        double rMult = regressionAdjustment(roleKey, lvl, yearsExp);

        double aLow = infl[0], aHigh = infl[1];
        double bLow = infl[0] * sMult, bHigh = infl[1] * sMult;
        double dLow = infl[0] * sMult * gMult, dHigh = infl[1] * sMult * gMult;
        double eLow = dLow * rMult, eHigh = dHigh * rMult;

        double wSkills = 0.20, wGeo = 0.20, wReg = 0.20;

        double low = (1 - wSkills) * aLow + wSkills * bLow;
        double high = (1 - wSkills) * aHigh + wSkills * bHigh;

        low = (1 - wGeo) * low + wGeo * dLow;
        high = (1 - wGeo) * high + wGeo * dHigh;

        low = (1 - wReg) * low + wReg * eLow;
        high = (1 - wReg) * high + wReg * eHigh;

        double mid = (low + high) / 2.0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roleKey", roleKey);
        out.put("level", lvl);
        out.put("finalLow", low);
        out.put("finalMid", mid);
        out.put("finalHigh", high);
        out.put("skillsMultiplier", sMult);
        out.put("geoMultiplier", gMult);
        out.put("regressionAdjustment", rMult);
        return out;
    }

    public static void main(String[] args) {
        List<Map<String, Object>> examples = new ArrayList<>();

        examples.add(predict2026(
            "Cybersecurity Engineer", "Mid", 4, "TX",
            Arrays.asList("siem", "soar", "zero_trust", "cissp")
        ));

        examples.add(predict2026(
            "Cloud Security Engineer", "Senior", 9, "CA",
            Arrays.asList("aws_security", "kubernetes", "terraform", "cnapp", "ccsp")
        ));

        examples.add(predict2026(
            "SOC Analyst", "Entry", 1, "REMOTE",
            Arrays.asList("siem", "edr", "security_plus")
        ));

        for (Map<String, Object> out : examples) {
            System.out.println("========================================================================");
            System.out.println(out.get("roleKey") + " (" + out.get("level") + ")");
            System.out.println("Predicted 2026: " + money((double) out.get("finalLow")) +
                               " – " + money((double) out.get("finalHigh")) +
                               " (mid " + money((double) out.get("finalMid")) + ")");
            System.out.println("Multipliers: skills=" + String.format("%.3f", (double) out.get("skillsMultiplier")) +
                               " geo=" + String.format("%.3f", (double) out.get("geoMultiplier")) +
                               " reg=" + String.format("%.3f", (double) out.get("regressionAdjustment")));
        }
    }
}
