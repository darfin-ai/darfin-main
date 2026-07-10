package com.kosta.darfin.service.analysis;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ReportFactApiIds {

    private ReportFactApiIds() {}

    public static final List<String> ALL = List.of(
            "alotMatter",
            "hyslrSttus",
            "hyslrChgSttus",
            "mrhlSttus",
            "empSttus",
            "tesstkAcqsDspsSttus",
            "irdsSttus",
            "stockTotqySttus",
            "exctvSttus",
            "accnutAdtorNmNdAdtOpinion"
    );

    public static final Map<String, String> API_TO_SECTION = Map.ofEntries(
            Map.entry("alotMatter", "dividends"),
            Map.entry("hyslrSttus", "majorShareholders"),
            Map.entry("hyslrChgSttus", "majorShareholderChanges"),
            Map.entry("mrhlSttus", "minorityShareholders"),
            Map.entry("empSttus", "employees"),
            Map.entry("tesstkAcqsDspsSttus", "treasuryStock"),
            Map.entry("irdsSttus", "capitalChanges"),
            Map.entry("stockTotqySttus", "stockTotals"),
            Map.entry("exctvSttus", "executives"),
            Map.entry("accnutAdtorNmNdAdtOpinion", "auditOpinions")
    );

    public static final Map<String, String> SECTION_LABELS = Map.ofEntries(
            Map.entry("dividends", "배당에 관한 사항"),
            Map.entry("majorShareholders", "최대주주 및 특수관계인의 주식소유 현황"),
            Map.entry("majorShareholderChanges", "최대주주 변동현황"),
            Map.entry("minorityShareholders", "소액주주 현황"),
            Map.entry("employees", "직원 등의 현황"),
            Map.entry("treasuryStock", "자기주식 취득 및 처분 현황"),
            Map.entry("capitalChanges", "증자(감자) 현황"),
            Map.entry("stockTotals", "주식의 총수 등"),
            Map.entry("executives", "임원 현황"),
            Map.entry("auditOpinions", "회계감사인의 명칭 및 감사의견")
    );

    /** alotMatter has its own embedded 3-year history — never backfilled from an older period. */
    public static final List<String> FALLBACK_ELIGIBLE = ALL.stream()
            .filter(id -> !id.equals("alotMatter"))
            .collect(Collectors.toUnmodifiableList());

    public static final int MAX_FALLBACK_CANDIDATES = 3;
    public static final int NEGATIVE_RETRY_HOURS = 24;
}
