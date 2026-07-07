package com.kosta.darfin.dto.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

// authKey: 프론트에서 tossPayments.requestBillingAuth() 성공 콜백으로 받은 값
@Getter
@NoArgsConstructor
public class RegisterCardRequest {

    @NotBlank(message = "authKey가 필요합니다.")
    private String authKey;
}
