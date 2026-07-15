package com.kosta.darfin.service.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * fnlttSinglAcntAll.json 응답 -&gt; 계정 행(metric rows) 변환 (구 metrics 테이블 shape — 2026-07-13 테이블 폐기).
 * Java port of darfin-company-analysis/dart_pipeline/metrics.py:transform() — 순수 변환
 * 로직(네트워크/DB 없음), 프론트 계약은 그대로 두고 소스만 라이브 fetch로 바꾸는 것이므로
 * Python 원본과 동일한 규칙을 그대로 따른다.
 */
public final class FinancialFactTransformer {

    private FinancialFactTransformer() {
    }

    private static final Map<String, String> STATEMENT_LABELS = Map.of(
            "BS", "재무상태표",
            "IS", "손익계산서",
            "CIS", "손익계산서", // 포괄손익계산서 — 손익과 구분하지 않는다 (Python과 동일)
            "CF", "현금흐름표"
    );
    // 자본변동표(SCE) 등은 위 4개에 대응이 없어 저장 대상에서 제외한다.

    private static final String NO_STANDARD_CODE = "-표준계정코드 미사용-";

    private static Integer parseOrd(Object text) {
        if (text == null) return null;
        try {
            return Integer.parseInt(String.valueOf(text).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseAmount(Object text) {
        if (text == null) return null;
        String cleaned = String.valueOf(text).replace(",", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("-")) return null;
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** rawRows: fnlttSinglAcntAll.json의 list 응답. */
    public static List<Map<String, Object>> transform(
            List<Map<String, Object>> rawRows,
            String rceptNo, String corpCode, String bsnsYear, String reprtCode, boolean isConsolidated
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : rawRows) {
            String statementType = STATEMENT_LABELS.get(item.get("sj_div"));
            if (statementType == null) continue;

            String accountNm = String.valueOf(item.getOrDefault("account_nm", "")).trim();
            if (accountNm.isEmpty()) continue;

            Object accountId = item.get("account_id");
            String concept = (accountId == null || NO_STANDARD_CODE.equals(accountId))
                    ? null : String.valueOf(accountId);

            Map<String, Object> base = new LinkedHashMap<>();
            base.put("rcept_no", rceptNo);
            base.put("corp_code", corpCode);
            base.put("bsns_year", bsnsYear);
            base.put("reprt_code", reprtCode);
            base.put("concept", concept);
            base.put("account_nm", accountNm);
            base.put("statement_type", statementType);
            base.put("ord", parseOrd(item.get("ord")));
            base.put("is_consolidated", isConsolidated);

            Long current = parseAmount(item.get("thstrm_amount"));
            Long cumulative = parseAmount(item.get("thstrm_add_amount"));

            if (cumulative != null) {
                if (current != null) {
                    Map<String, Object> row = new LinkedHashMap<>(base);
                    row.put("period_qualifier", "3개월");
                    row.put("amount", current);
                    rows.add(row);
                }
                Map<String, Object> row = new LinkedHashMap<>(base);
                row.put("period_qualifier", "누적");
                row.put("amount", cumulative);
                rows.add(row);
            } else if (current != null) {
                Map<String, Object> row = new LinkedHashMap<>(base);
                row.put("period_qualifier", null);
                row.put("amount", current);
                rows.add(row);
            }
        }
        return rows;
    }
}
