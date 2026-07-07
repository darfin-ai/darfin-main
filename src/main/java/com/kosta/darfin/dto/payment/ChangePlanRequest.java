package com.kosta.darfin.dto.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Getter
@NoArgsConstructor
public class ChangePlanRequest {

    @NotBlank(message = "변경할 요금제를 선택해주세요.")
    private String planName;
}
