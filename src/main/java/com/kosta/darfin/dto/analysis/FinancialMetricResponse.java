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
