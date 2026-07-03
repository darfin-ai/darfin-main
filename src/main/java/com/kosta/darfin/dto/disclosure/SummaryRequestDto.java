package com.kosta.darfin.dto.disclosure;

public class SummaryRequestDto {

    private String rceptNo;
    private String corpName;
    private String dartContext;

    public String getRceptNo() {
        return rceptNo;
    }

    public void setRceptNo(String rceptNo) {
        this.rceptNo = rceptNo;
    }

    public String getCorpName() {
        return corpName;
    }

    public void setCorpName(String corpName) {
        this.corpName = corpName;
    }

    public String getDartContext() {
        return dartContext;
    }

    public void setDartContext(String dartContext) {
        this.dartContext = dartContext;
    }
}
