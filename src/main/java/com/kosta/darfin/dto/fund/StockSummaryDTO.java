package com.kosta.darfin.dto.fund;

import lombok.Getter;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * 프론트 RAW_STOCKS와 동일한 필드 구조로 맞춘 응답 DTO.
 */
@Getter
@AllArgsConstructor
public class StockSummaryDTO {
    private String code;
    private String name;
    private Long price;
    private BigDecimal pct;
    private Long value;
    private String logoUrl;
}