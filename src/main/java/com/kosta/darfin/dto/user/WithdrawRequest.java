package com.kosta.darfin.dto.user;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WithdrawRequest {

    // LOCAL 계정은 본인 확인용 비밀번호 필수, 소셜 계정은 null 허용
    private String currentPassword;
}
