package com.kosta.darfin.dto.fund;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class OrderResponse {
    private Long   tradeId;
    private String stockCode;
    private String stockName;
    private String side;        // BUY | SELL
    private String orderType;   // LIMIT | MARKET
    private Long   price;
    private Integer quantity;
    private Long   totalAmount;
    private Long   realizedPnl; // 매도 시만 값 존재
    private LocalDateTime tradedAt;
}
