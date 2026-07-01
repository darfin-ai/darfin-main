package com.kosta.darfin.dto.fund;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExecutionResponse {
    private Long   price;
    private Long   quantity;
    private Double changeRate;
    private String time;   // "HH:MM:SS"
}
