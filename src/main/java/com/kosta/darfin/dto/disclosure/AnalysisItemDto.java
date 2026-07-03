package com.kosta.darfin.dto.disclosure;

public class AnalysisItemDto {

    private String analysisCategory;   // Market_Competitiveness 등
    private String targetKey;          // 원문 핵심 하이라이트 문장
    private String materialImpact;     // 영향 분석 (3문장 이내)
    private String riskLevel;          // Gemini가 돌려준 원본 라벨(Low|Neutral|High|Critical 등)
    private Byte riskTier;             // risk_scale 조회로 채워지는 정규화 단계(1~5). 초기에는 null.
    private int charOffsetStart;       // 원문 기준 시작 위치
    private int charOffsetEnd;         // 원문 기준 종료 위치

    public AnalysisItemDto() {}

    public AnalysisItemDto(String analysisCategory, String targetKey,
                            String materialImpact, String riskLevel,
                            int charOffsetStart, int charOffsetEnd) {
        this.analysisCategory = analysisCategory;
        this.targetKey        = targetKey;
        this.materialImpact   = materialImpact;
        this.riskLevel        = riskLevel;
        this.charOffsetStart  = charOffsetStart;
        this.charOffsetEnd    = charOffsetEnd;
    }

    public String getAnalysisCategory()            { return analysisCategory; }
    public void   setAnalysisCategory(String v)    { this.analysisCategory = v; }
    public String getTargetKey()                   { return targetKey; }
    public void   setTargetKey(String v)           { this.targetKey = v; }
    public String getMaterialImpact()              { return materialImpact; }
    public void   setMaterialImpact(String v)      { this.materialImpact = v; }
    public String getRiskLevel()                   { return riskLevel; }
    public void   setRiskLevel(String v)           { this.riskLevel = v; }
    public Byte   getRiskTier()                    { return riskTier; }
    public void   setRiskTier(Byte v)              { this.riskTier = v; }
    public int    getCharOffsetStart()             { return charOffsetStart; }
    public void   setCharOffsetStart(int v)        { this.charOffsetStart = v; }
    public int    getCharOffsetEnd()               { return charOffsetEnd; }
    public void   setCharOffsetEnd(int v)          { this.charOffsetEnd = v; }
}
