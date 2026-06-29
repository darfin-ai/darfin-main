package com.kosta.darfin.dto.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FindIdResponse {

    private String email;
    private String provider;  // LOCAL | KAKAO | GOOGLE
}
