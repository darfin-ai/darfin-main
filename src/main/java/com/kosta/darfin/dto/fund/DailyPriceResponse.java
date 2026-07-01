package com.kosta.darfin.dto.fund;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DailyPriceResponse {
    private String date;        // "YYYY-MM-DD"
    private Long   closePrice;
    private Double changeRate;  // 전일 대비 등락율 (%)
    private Long   volume;
}
