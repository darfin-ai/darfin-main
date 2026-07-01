package com.kosta.darfin.dto.fund;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class MarketMetricDTO {
    private String code;
    private String name;
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal pct;
    private Long volume;
    private Long value;
}

