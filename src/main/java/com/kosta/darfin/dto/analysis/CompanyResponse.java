package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyResponse {
    private String id; // corp_code
    private String name;
    private String ticker;
    private String sector;
    private String latestFilingType;
    private String latestFilingDate;
    private String changeSummary;
    private Integer marketCapRank;
    private Integer kosdaqRank;
    private String marketCap;
}
