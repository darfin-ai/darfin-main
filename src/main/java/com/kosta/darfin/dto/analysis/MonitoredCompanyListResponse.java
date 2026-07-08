package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MonitoredCompanyListResponse {
    private List<MonitoredCompanyResponse> items;
    private int count;
    private int limit;
}
