package com.kosta.darfin.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ScoreComponentResponse {
    private String key;
    private int maxPoints;
    private List<HistoryPoint> history;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class HistoryPoint {
        private String quarter;
        private double value;
    }
}
