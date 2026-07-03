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
    private Long value;   // 거래대금 — 억원 단위
    private Long volume;  // 거래량 — 주 단위
    private String logoUrl;
}