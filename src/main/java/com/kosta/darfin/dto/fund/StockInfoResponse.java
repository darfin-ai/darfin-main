package com.kosta.darfin.dto.fund;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockInfoResponse {
    private Long   marketCap;   // 시가총액 (원 단위)
    private Long   week52High;  // 52주 최고가
    private Long   week52Low;   // 52주 최저가
    private Double per;         // PER (적자 종목은 null)
    private String sector;      // 업종명 (없으면 null)
}
