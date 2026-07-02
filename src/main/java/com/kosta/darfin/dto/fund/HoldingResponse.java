package com.kosta.darfin.dto.fund;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HoldingResponse {
    private String stockCode;
    private String stockName;
    private Integer quantity;
    private Long    avgBuyPrice;
    private Long    currentPrice;
    private Long    valuationPnl;      // (currentPrice - avgBuyPrice) × quantity
    private Double  valuationPnlRate;  // 수익률 (%)
}
