package com.kosta.darfin.service.oauth;

import com.kosta.darfin.dto.oauth.GoogleTokenResponse;
import com.kosta.darfin.dto.oauth.GoogleUserInfo;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private static final String TOKEN_URL    = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    @Value("${oauth2.google.client-id}")
    private String clientId;

    @Value("${oauth2.google.client-secret}")
    private String clientSecret;

    @Value("${oauth2.google.redirect-uri}")
    private String redirectUri;

    private final RestTemplate restTemplate;

    /**
     * 인가 코드(code)를 구글 Access Token으로 교환한다.
     */
    public String getAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code",          code);
        params.add("client_id",     clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri",  redirectUri);
        params.add("grant_type",    "authorization_code");

        try {
            GoogleTokenResponse response = restTemplate.postForObject(
                    TOKEN_URL,
                    new HttpEntity<>(params, headers),
                    GoogleTokenResponse.class
            );

            if (response == null || response.getAccessToken() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "구글 토큰 발급에 실패했습니다.");
            }
            return response.getAccessToken();

        } catch (RestClientException e) {
            log.error("구글 토큰 요청 실패: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "구글 서버와 통신에 실패했습니다.");
        }
    }

    /**
     * 구글 Access Token으로 사용자 정보를 조회한다.
     */
    public GoogleUserInfo getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<GoogleUserInfo> response = restTemplate.exchange(
                    USER_INFO_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    GoogleUserInfo.class
            );

            GoogleUserInfo userInfo = response.getBody();
            if (userInfo == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "구글 사용자 정보 조회에 실패했습니다.");
            }
            return userInfo;

        } catch (RestClientException e) {
            log.error("구글 사용자 정보 요청 실패: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "구글 서버와 통신에 실패했습니다.");
        }
    }
}
