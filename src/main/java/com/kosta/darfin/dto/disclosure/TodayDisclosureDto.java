package com.kosta.darfin.dto.disclosure;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * "오늘 올라온 공시" 피드 1건. DART Open API는 접수 시:분을 주지 않으므로,
 * detectedAt은 실제 DART 접수시각이 아니라 우리 서버가 이 공시를 처음
 * 감지(UPSERT)한 시각이다 — DisclosureTodayService 참고.
 */
public class TodayDisclosureDto {

    private final String rceptNo;
    private final String title;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private final LocalDate filedAt;

    private final String typeCode;
    private final String typeName;
    private final String companyName;
    private final String filerName;
    private final LocalDateTime detectedAt;

    public TodayDisclosureDto(String rceptNo, String title, LocalDate filedAt,
                               String typeCode, String typeName, String companyName,
                               String filerName, LocalDateTime detectedAt) {
        this.rceptNo = rceptNo;
        this.title = title;
        this.filedAt = filedAt;
        this.typeCode = typeCode;
        this.typeName = typeName;
        this.companyName = companyName;
        this.filerName = filerName;
        this.detectedAt = detectedAt;
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

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }
}
