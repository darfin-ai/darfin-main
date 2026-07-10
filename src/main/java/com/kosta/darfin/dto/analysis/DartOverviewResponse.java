package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DartOverviewResponse {
    private Meta meta;
    private DartOverviewSection dividends;
    private DartOverviewSection majorShareholders;
    private DartOverviewSection majorShareholderChanges;
    private DartOverviewSection minorityShareholders;
    private DartOverviewSection employees;
    private DartOverviewSection treasuryStock;
    private DartOverviewSection capitalChanges;
    private DartOverviewSection stockTotals;
    private DartOverviewSection executives;
    private DartOverviewSection auditOpinions;

    @Getter
    @Builder
    public static class Meta {
        private String bsnsYear;
        private String reprtCode;
        private String rceptNo;
    }
}
