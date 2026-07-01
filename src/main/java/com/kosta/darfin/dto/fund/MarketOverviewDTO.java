package com.kosta.darfin.dto.fund;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MarketOverviewDTO {
    private List<MarketMetricDTO> indices;
    private MarketMetricDTO usdKrw;
}

