package com.kosta.darfin.dto.disclosure;

public class SummaryResponseDto {

    private boolean success;
    private String  summaryText;
    private String  investorComment;
    private String  overallRisk;
    private String  modelName;
    private int     tokensIn;
    private int     tokensOut;
    private double  costUsd;
    private long    latencyMs;
    private String  errorMessage;

    // ── static factory ────────────────────────────────────────────────
    public static SummaryResponseDto ok(String summaryText, String investorComment,
                                         String overallRisk, String modelName, int tokensIn,
                                         int tokensOut, double costUsd, long latencyMs) {
        SummaryResponseDto dto = new SummaryResponseDto();
        dto.success         = true;
        dto.summaryText     = summaryText;
        dto.investorComment = investorComment;
        dto.overallRisk     = overallRisk;
        dto.modelName       = modelName;
        dto.tokensIn        = tokensIn;
        dto.tokensOut       = tokensOut;
        dto.costUsd         = costUsd;
        dto.latencyMs       = latencyMs;
        return dto;
    }

    public static SummaryResponseDto error(String message) {
        return error(message, null);
    }

    /** modelName은 Python 서비스가 호출을 시도한 뒤 응답 JSON에 실어 보낸 값 — 네트워크 자체가 실패하면 null일 수 있다. */
    public static SummaryResponseDto error(String message, String modelName) {
        SummaryResponseDto dto = new SummaryResponseDto();
        dto.success      = false;
        dto.errorMessage = message;
        dto.modelName     = modelName;
        return dto;
    }

    // ── getters ───────────────────────────────────────────────────────
    public boolean isSuccess()             { return success; }
    public String  getSummaryText()        { return summaryText; }
    public String  getInvestorComment()    { return investorComment; }
    public String  getOverallRisk()        { return overallRisk; }
    public String  getModelName()          { return modelName; }
    public int     getTokensIn()           { return tokensIn; }
    public int     getTokensOut()          { return tokensOut; }
    public double  getCostUsd()            { return costUsd; }
    public long    getLatencyMs()          { return latencyMs; }
    public String  getErrorMessage()       { return errorMessage; }
}
