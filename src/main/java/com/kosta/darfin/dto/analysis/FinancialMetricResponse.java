package com.kosta.darfin.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FinancialMetricResponse {
    private String concept;
    private String label;
    /** 재무상태표 / 손익계산서 / 현금흐름표 — 공시 원문의 재무제표 구분 */
    private String statementType;
    private String unit;
    private List<SeriesPoint> series;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class SeriesPoint {
        private String quarter;
        private long value;
    }
}
