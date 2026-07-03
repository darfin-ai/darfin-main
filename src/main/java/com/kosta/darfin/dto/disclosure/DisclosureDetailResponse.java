package com.kosta.darfin.dto.disclosure;

import java.time.LocalDate;
import java.util.List;

public class DisclosureDetailResponse {

    private String rceptNo;
    private String title;
    private LocalDate filedAt;
    private String typeCode;
    private String typeLabel;
    private String companyName;
    private String corpCode;
    private String filerName;

    // 요약 (ai_summary_result가 없으면 전부 null)
    private String summaryText;
    private String investorComment;
    private String riskLabel;
    private Byte riskTier;
    private String extra; // JSON 문자열(auditOpinion, isTrueSaleConfirmed 등 보고서별 가변 필드)

    // 분석 (ai_analysis_item, 없으면 빈 리스트)
    private List<AnalysisItemDto> analysisItems;

    public String getRceptNo() {
        return rceptNo;
    }

    public void setRceptNo(String rceptNo) {
        this.rceptNo = rceptNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getFiledAt() {
        return filedAt;
    }

    public void setFiledAt(LocalDate filedAt) {
        this.filedAt = filedAt;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public void setTypeLabel(String typeLabel) {
        this.typeLabel = typeLabel;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCorpCode() {
        return corpCode;
    }

    public void setCorpCode(String corpCode) {
        this.corpCode = corpCode;
    }

    public String getFilerName() {
        return filerName;
    }

    public void setFilerName(String filerName) {
        this.filerName = filerName;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public String getInvestorComment() {
        return investorComment;
    }

    public void setInvestorComment(String investorComment) {
        this.investorComment = investorComment;
    }

    public String getRiskLabel() {
        return riskLabel;
    }

    public void setRiskLabel(String riskLabel) {
        this.riskLabel = riskLabel;
    }

    public Byte getRiskTier() {
        return riskTier;
    }

    public void setRiskTier(Byte riskTier) {
        this.riskTier = riskTier;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public List<AnalysisItemDto> getAnalysisItems() {
        return analysisItems;
    }

    public void setAnalysisItems(List<AnalysisItemDto> analysisItems) {
        this.analysisItems = analysisItems;
    }
}
