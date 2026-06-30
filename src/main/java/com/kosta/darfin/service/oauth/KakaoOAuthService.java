package com.kosta.darfin.service.oauth;

import com.kosta.darfin.dto.oauth.KakaoTokenResponse;
import com.kosta.darfin.dto.oauth.KakaoUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuthService {

    private static final String TOKEN_URL    = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    @Value("${oauth2.kakao.client-id}")
    private String clientId;

    @Value("${oauth2.kakao.client-secret}")
    private String clientSecret;

    @Value("${oauth2.kakao.redirect-uri}")
    private String redirectUri;

    private final RestTemplate restTemplate;

    public String getAuthorizationUrl() {
        return "https://kauth.kakao.com/oauth/authorize"
                + "?client_id="     + clientId
                + "&redirect_uri="  + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=profile_nickname,profile_image,account_email";
    }

    /**
     * 인가 코드(code)를 카카오 Access Token으로 교환한다.
     */
    public String getAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type",    "authorization_code");
        params.add("client_id",     clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri",  redirectUri);
        params.add("code",          code);

        try {
            KakaoTokenResponse response = restTemplate.postForObject(
                    TOKEN_URL,
                    new HttpEntity<>(params, headers),
                    KakaoTokenResponse.class
            );

            if (response == null || response.getAccessToken() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 토큰 발급에 실패했습니다.");
            }
            return response.getAccessToken();

        } catch (RestClientException e) {
            log.error("카카오 토큰 요청 실패: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 서버와 통신에 실패했습니다.");
        }
    }

    /**
     * 카카오 Access Token으로 사용자 정보를 조회한다.
     */
    public KakaoUserInfo getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<KakaoUserInfo> response = restTemplate.exchange(
                    USER_INFO_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    KakaoUserInfo.class
            );

            KakaoUserInfo userInfo = response.getBody();
            if (userInfo == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 사용자 정보 조회에 실패했습니다.");
            }
            if (userInfo.getEmail() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "카카오 이메일 제공에 동의해주세요. 이메일 정보가 필요합니다.");
            }
            return userInfo;

        } catch (RestClientException e) {
            log.error("카카오 사용자 정보 요청 실패: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 서버와 통신에 실패했습니다.");
        }
    }
}
