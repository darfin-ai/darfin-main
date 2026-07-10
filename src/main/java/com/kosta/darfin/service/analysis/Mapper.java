package com.kosta.darfin.service.analysis;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Mapper {

    // DART meta fields (disclosure identifiers) stripped from mapped output — verbatim from report_facts.py:_META_KEYS
    private static final Set<String> META_KEYS = Set.of(
            "rcept_no", "corp_cls", "corp_code", "corp_name", "stlm_dt", "rm");

    // row-label field, excluded from placeholder value comparison — report_facts.py:_LABEL_KEYS
    private static final Set<String> LABEL_KEYS = Set.of("se");

    // report_facts.py:_PLACEHOLDER_VALUES — note the em-dash "－" (U+FF0D), not a regular hyphen
    private static final Set<String> PLACEHOLDER_VALUES = new HashSet<>(Arrays.asList(null, "", "-", "－"));

    // dart_overview_compose.py:_FIELD_ALIASES — copy every entry verbatim, do not abbreviate this list
    private static final Map<String, String> FIELD_ALIASES = Map.ofEntries(
            Map.entry("bsis_posesn_stock_co", "bsisPosesnStockCo"),
            Map.entry("bsis_posesn_stock_qota_rt", "bsisQotaRt"),
            Map.entry("trmend_posesn_stock_co", "trmendPosesnStockCo"),
            Map.entry("trmend_posesn_stock_qota_rt", "trmendQotaRt"),
            Map.entry("fo_bbm", "foBbm"),
            Map.entry("rgllbr_co", "rgllbrCo"),
            Map.entry("cnttk_co", "cnttkCo"),
            Map.entry("avrg_cnwk_sdytrn", "avrgCnwkSdytrn"),
            Map.entry("fyer_salary_totamt", "fyerSalaryTotamt"),
            Map.entry("jan_salary_am", "janSalaryAm"),
            Map.entry("acqs_mth1", "acqsMth1"),
            Map.entry("acqs_mth2", "acqsMth2"),
            Map.entry("acqs_mth3", "acqsMth3"),
            Map.entry("stock_knd", "stockKnd"),
            Map.entry("bsis_qy", "bsisQy"),
            Map.entry("change_qy_acqs", "changeQyAcqs"),
            Map.entry("change_qy_dsps", "changeQyDsps"),
            Map.entry("change_qy_incnr", "changeQyIncnr"),
            Map.entry("trmend_qy", "trmendQy"),
            Map.entry("isu_dcrs_de", "isuDcrsDe"),
            Map.entry("isu_dcrs_stle", "isuDcrsStle"),
            Map.entry("isu_dcrs_stock_knd", "isuDcrsStockKnd"),
            Map.entry("isu_dcrs_qy", "isuDcrsQy"),
            Map.entry("isu_dcrs_mstvdiv_fval_amount", "isuDcrsMstvdivFvalAmount"),
            Map.entry("isu_dcrs_mstvdiv_amount", "isuDcrsMstvdivAmount"),
            Map.entry("isu_stock_totqy", "isuStockTotqy"),
            Map.entry("istc_totqy", "istcTotqy"),
            Map.entry("tesstk_co", "tesstkCo"),
            Map.entry("distb_stock_co", "distbStockCo"),
            Map.entry("birth_ym", "birthYm"),
            Map.entry("rgist_exctv_at", "rgistExctvAt"),
            Map.entry("fte_at", "fteAt"),
            Map.entry("chrg_job", "chrgJob"),
            Map.entry("main_career", "mainCareer"),
            Map.entry("hffc_pd", "hffcPd"),
            Map.entry("tenure_end_on", "tenureEndOn"),
            Map.entry("change_on", "changeOn"),
            Map.entry("mxmm_shrholdr_nm", "mxmmShrholdrNm"),
            Map.entry("posesn_stock_co", "posesnStockCo"),
            Map.entry("change_cause", "changeCause"),
            Map.entry("shrholdr_co", "shrholdrCo"),
            Map.entry("shrholdr_tot_co", "shrholdrTotCo"),
            Map.entry("shrholdr_rate", "shrholdrRate"),
            Map.entry("hold_stock_co", "holdStockCo"),
            Map.entry("stock_tot_co", "stockTotCo"),
            Map.entry("hold_stock_rate", "holdStockRate"),
            Map.entry("adt_opinion", "adtOpinion"),
            Map.entry("emphs_matter", "emphsMatter"),
            Map.entry("core_adt_matter", "coreAdtMatter"),
            Map.entry("bsns_year", "bsnsYear")
    );

    // dart_overview_compose.py:_NUMERIC_FIELDS — copy verbatim (camelCase names, post-alias)
    private static final Set<String> NUMERIC_FIELDS = Set.of(
            "thstrm", "frmtrm", "lwfr", "bsisPosesnStockCo", "bsisQotaRt", "trmendPosesnStockCo",
            "trmendQotaRt", "posesnStockCo", "qotaRt", "shrholdrCo", "shrholdrTotCo", "shrholdrRate",
            "holdStockCo", "stockTotCo", "holdStockRate", "rgllbrCo", "cnttkCo", "sm", "fyerSalaryTotamt",
            "janSalaryAm", "bsisQy", "changeQyAcqs", "changeQyDsps", "changeQyIncnr", "trmendQy",
            "isuDcrsQy", "isuDcrsMstvdivFvalAmount", "isuDcrsMstvdivAmount", "isuStockTotqy", "istcTotqy",
            "redc", "tesstkCo", "distbStockCo"
    );

    private static final Set<String> DATE_FIELDS = Set.of("changeOn", "isuDcrsDe", "tenureEndOn");

    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{4})[.\\-년/\\s]+(\\d{1,2})[.\\-월/\\s]+(\\d{1,2})");

    /** DART returned status 000 but every field is a placeholder dash — treat as "no data", same as 013. */
    public boolean isPlaceholderOnly(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return true;
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (META_KEYS.contains(e.getKey()) || LABEL_KEYS.contains(e.getKey())) continue;
                Object v = e.getValue();
                String s = v == null ? null : String.valueOf(v);
                if (!PLACEHOLDER_VALUES.contains(s)) return false;
            }
        }
        return true;
    }

    /** Raw snake_case DART rows for one api_id -> camelCase mapped rows, per dart_overview_compose.py:_map_row. */
    public List<Map<String, Object>> mapRows(List<Map<String, Object>> rawRows, String apiId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rawRows) {
            out.add(mapRow(row, apiId));
        }
        return out;
    }

    private Map<String, Object> mapRow(Map<String, Object> row, String apiId) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String key = e.getKey();
            if (META_KEYS.contains(key)) continue;
            Object value = e.getValue();
            String camel = snakeToCamel(key);
            if (camel.equals("stockKnd")) {
                out.put(camel, normalizeStockKnd(value == null ? "" : String.valueOf(value)));
            } else if (NUMERIC_FIELDS.contains(camel)) {
                out.put(camel, parseNum(value == null ? null : String.valueOf(value)));
            } else if (DATE_FIELDS.contains(camel)) {
                out.put(camel, normalizeDate(value == null ? null : String.valueOf(value)));
            } else if (camel.equals("bsnsYear") && "accnutAdtorNmNdAdtOpinion".equals(apiId)) {
                out.put(camel, value == null ? "" : String.valueOf(value).trim());
            } else {
                out.put(camel, value == null ? "" : String.valueOf(value).trim());
            }
        }
        if ("alotMatter".equals(apiId)) {
            Object se = out.get("se");
            if (se != null && String.valueOf(se).contains("백만원")) {
                for (String term : List.of("thstrm", "frmtrm", "lwfr")) {
                    Object v = out.get(term);
                    if (v instanceof Number) {
                        Number n = (Number) v;
                        out.put(term, (long) (n.doubleValue() * 1_000_000));
                    }
                }
            }
        }
        return out;
    }

    private String snakeToCamel(String key) {
        if (FIELD_ALIASES.containsKey(key)) return FIELD_ALIASES.get(key);
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    // returns Long, Double, or null — mirrors Python's int/float duck-typing (int() when no ".", float() otherwise)
    private Object parseNum(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.isEmpty() || s.equals("-") || s.equals("－")) return null;
        String cleaned = s.replace(",", "").replace("%", "").trim();
        try {
            if (cleaned.contains(".")) return Double.parseDouble(cleaned);
            return Long.parseLong(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeDate(String text) {
        if (text == null || text.trim().isEmpty() || text.trim().equals("-")) return null;
        String s = text.trim();
        Matcher m = DATE_PATTERN.matcher(s);
        if (m.find()) {
            String y = m.group(1);
            int mo = Integer.parseInt(m.group(2));
            int d = Integer.parseInt(m.group(3));
            return String.format("%s-%02d-%02d", y, mo, d);
        }
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) return s;
        return s;
    }

    private String normalizeStockKnd(String value) {
        if (value == null) return null;
        String s = value.trim();
        if (s.isEmpty() || s.equals("-") || s.equals("－")) return null;
        return s;
    }
}
