package com.kosta.darfin.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReissueRequest {

    @NotBlank(message = "Refresh Token은 필수입니다.")
    private String refreshToken;
}
