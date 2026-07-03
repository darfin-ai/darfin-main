package com.kosta.darfin.dto.disclosure;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

public class DisclosureSearchResult {

    private final String rceptNo;
    private final String title;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private final LocalDate filedAt;

    private final String typeCode;
    private final String typeName;
    private final String companyName;
    private final String filerName;
    private final String riskLabel;   // ai_summary_result가 없으면 null(요약 전 상태)
    private final Byte riskTier;      // 〃

    public DisclosureSearchResult(String rceptNo, String title, LocalDate filedAt,
                                   String typeCode, String typeName, String companyName,
                                   String filerName, String riskLabel, Byte riskTier) {
        this.rceptNo = rceptNo;
        this.title = title;
        this.filedAt = filedAt;
        this.typeCode = typeCode;
        this.typeName = typeName;
        this.companyName = companyName;
        this.filerName = filerName;
        this.riskLabel = riskLabel;
        this.riskTier = riskTier;
    }

    public String getRceptNo() {
        return rceptNo;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getFiledAt() {
        return filedAt;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getFilerName() {
        return filerName;
    }

    public String getRiskLabel() {
        return riskLabel;
    }

    public Byte getRiskTier() {
        return riskTier;
    }
}
