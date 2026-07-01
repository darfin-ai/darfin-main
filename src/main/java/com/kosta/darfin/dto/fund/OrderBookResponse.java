package com.kosta.darfin.dto.fund;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class OrderBookResponse {

    private Long   currentPrice;
    private Double changeRate;

    /** 매도호가 — askp1(최우선) ~ askp5 순 (프론트에서 역순 표시) */
    private List<OrderItem> asks;

    /** 매수호가 — bidp1(최우선) ~ bidp5 순 */
    private List<OrderItem> bids;

    @Getter
    @Builder
    public static class OrderItem {
        private Long price;
        private Long quantity;
    }
}
