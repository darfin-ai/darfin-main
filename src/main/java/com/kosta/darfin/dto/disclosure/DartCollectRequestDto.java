package com.kosta.darfin.dto.disclosure;


public class DartCollectRequestDto {

    private String companyName;
    private String bgnDe;
    private String endDe;

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getBgnDe() { return bgnDe; }
    public void setBgnDe(String bgnDe) { this.bgnDe = bgnDe; }

    public String getEndDe() { return endDe; }
    public void setEndDe(String endDe) { this.endDe = endDe; }
}
