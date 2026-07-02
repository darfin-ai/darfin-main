package com.kosta.darfin.dto.fund;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BalanceResponse {
    private Long availableBalance;
}
