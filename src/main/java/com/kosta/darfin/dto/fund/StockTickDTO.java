package com.kosta.darfin.dto.fund;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * KIS H0STCNT0(실시간 체결) 틱을 /topic/price/{code} 로 브로드캐스트할 때 쓰는 DTO.
 */
@Getter
@AllArgsConstructor
public class StockTickDTO {
    private String code;
    private Long price;
    private Double pct;
    private Long volume;      // 누적거래량 (ACML_VOL)
    private Long tradeValue;  // 누적거래대금 — 억원 단위 (StockRankService와 동일 환산)
    private String time;
}
