package com.kosta.darfin.dto.disclosure;

public class AnalysisRequestDto {

    private String rceptNo;        // DB 저장 대상 식별자(필수)
    private String corpName;       // 기업명
    private String dartFullText;   // 사업보고서 원문 전체 (압축 X — 좌표 보존 필요)

    public String getRceptNo()              { return rceptNo; }
    public void   setRceptNo(String v)      { this.rceptNo = v; }
    public String getCorpName()             { return corpName; }
    public void   setCorpName(String v)     { this.corpName = v; }
    public String getDartFullText()         { return dartFullText; }
    public void   setDartFullText(String v) { this.dartFullText = v; }
}
