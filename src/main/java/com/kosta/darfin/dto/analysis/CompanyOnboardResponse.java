package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyOnboardResponse {
    private String corpCode;
    private String name;
    private String ticker;
    private boolean newlyCreated;
}
