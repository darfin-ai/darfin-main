package com.kosta.darfin.dto.fund;

import lombok.Getter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
public class OrderRequest {

    @NotBlank
    private String stockCode;

    /** LIMIT(지정가) | MARKET(시장가) */
    @NotBlank
    private String orderType;

    /** 지정가 주문 시 필수. 시장가 주문 시 null 허용 (현재가 자동 조회). */
    private Long price;

    @NotNull
    @Min(1)
    private Integer quantity;
}
