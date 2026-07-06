package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecentFilingResponse {
    private String id;
    private String type;
    private String period;
    private String date;
    private String dartUrl;
}
