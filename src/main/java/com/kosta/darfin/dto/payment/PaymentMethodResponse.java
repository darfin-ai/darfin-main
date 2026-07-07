package com.kosta.darfin.dto.payment;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentMethodResponse {
    private Long id;
    private String cardCompany;
    private String maskedCardNum;
    private boolean isDefault;
}
