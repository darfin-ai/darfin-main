package com.kosta.darfin.dto.disclosure;

import java.time.LocalDate;
import java.util.List;

public class DisclosureSearchCondition {

    private String companyName;        // "기업명 또는 종목코드" 입력값 - 회사명/종목코드 둘 다로 매칭
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private List<String> typeCodes;    // 빈 리스트(또는 null) = "전체보기"
    private String sortKey;            // "date" | "type" | "title" | "risk"
    private String sortDirection;      // "asc" | "desc"

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    public List<String> getTypeCodes() {
        return typeCodes;
    }

    public void setTypeCodes(List<String> typeCodes) {
        this.typeCodes = typeCodes;
    }

    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    public boolean isAllTypesSelected() {
        return typeCodes == null || typeCodes.isEmpty();
    }
}
