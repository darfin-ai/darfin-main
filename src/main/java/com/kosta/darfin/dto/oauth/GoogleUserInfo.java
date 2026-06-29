package com.kosta.darfin.dto.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 구글 /oauth2/v3/userinfo 응답 매핑
 * {
 *   "sub": "1234567890",
 *   "email": "user@gmail.com",
 *   "name": "홍길동",
 *   "picture": "https://...",
 *   "email_verified": true
 * }
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleUserInfo {

    private String sub;
    private String email;
    private String name;
    private String picture;

    @JsonProperty("email_verified")
    private Boolean emailVerified;
}
