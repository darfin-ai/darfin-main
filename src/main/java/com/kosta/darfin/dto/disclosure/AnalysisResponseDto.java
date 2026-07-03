package com.kosta.darfin.dto.disclosure;

import java.util.List;

public class AnalysisResponseDto {

    private boolean success;
    private List<AnalysisItemDto> items;
    private int droppedCount;       // LLM 서비스에서 targetKey 원문불일치로 버려진 항목 수
    private int tokensIn;
    private int tokensOut;
    private double costUsd;
    private long latencyMs;
    private String errorMessage;

    public static AnalysisResponseDto ok(List<AnalysisItemDto> items, int droppedCount,
                                          int tokensIn, int tokensOut,
                                          double costUsd, long latencyMs) {
        AnalysisResponseDto dto = new AnalysisResponseDto();
        dto.success      = true;
        dto.items        = items;
        dto.droppedCount = droppedCount;
        dto.tokensIn     = tokensIn;
        dto.tokensOut    = tokensOut;
        dto.costUsd      = costUsd;
        dto.latencyMs    = latencyMs;
        return dto;
    }

    public static AnalysisResponseDto error(String msg) {
        AnalysisResponseDto dto = new AnalysisResponseDto();
        dto.success      = false;
        dto.errorMessage = msg;
        return dto;
    }

    public boolean isSuccess()                  { return success; }
    public List<AnalysisItemDto> getItems()      { return items; }
    public int    getDroppedCount()              { return droppedCount; }
    public int    getTokensIn()                  { return tokensIn; }
    public int    getTokensOut()                 { return tokensOut; }
    public double getCostUsd()                   { return costUsd; }
    public long   getLatencyMs()                 { return latencyMs; }
    public String getErrorMessage()              { return errorMessage; }
}
