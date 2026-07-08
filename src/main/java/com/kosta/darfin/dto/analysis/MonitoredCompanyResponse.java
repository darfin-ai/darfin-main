package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonitoredCompanyResponse {
    private String corpCode;
    private String name;
    private String ticker;
    private String addedAt;
}
