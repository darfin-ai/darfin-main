package com.kosta.darfin.dto.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RefundRequest {
    private String reason;
}
