package com.kosta.darfin.controller;

import com.kosta.darfin.dto.auth.LoginRequest;
import com.kosta.darfin.dto.auth.ReissueRequest;
import com.kosta.darfin.dto.auth.SignupRequest;
import com.kosta.darfin.dto.auth.TokenResponse;
import com.kosta.darfin.dto.oauth.KakaoUserInfo;
import com.kosta.darfin.dto.oauth.GoogleUserInfo;
import com.kosta.darfin.service.AuthService;
import com.kosta.darfin.service.oauth.KakaoOAuthService;
import com.kosta.darfin.service.oauth.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final KakaoOAuthService kakaoOAuthService;
    private final GoogleOAuthService googleOAuthService;

    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.login(request, ipAddress, userAgent));
    }

    // -------------------------------------------------------------------------
    // 소셜 로그인 콜백
    // -------------------------------------------------------------------------

    /**
     * 카카오 OAuth2 콜백
     * 프론트엔드가 카카오 인가 코드를 이 엔드포인트로 전달하면
     * Access/Refresh Token을 발급하여 반환한다.
     *
     * 카카오 인가 URL 예시:
     * https://kauth.kakao.com/oauth/authorize
     *   ?client_id={REST_API_KEY}
     *   &redirect_uri=http://localhost:8080/api/v1/auth/callback/kakao
     *   &response_type=code
     *   &scope=profile_nickname,profile_image,account_email
     */
    @GetMapping("/callback/kakao")
    public ResponseEntity<TokenResponse> kakaoCallback(@RequestParam String code,
                                                       HttpServletRequest httpRequest) {
        String kakaoAccessToken = kakaoOAuthService.getAccessToken(code);
        KakaoUserInfo userInfo  = kakaoOAuthService.getUserInfo(kakaoAccessToken);

        TokenResponse response = authService.socialLogin(
                userInfo.getEmail(),
                userInfo.getNickname(),
                userInfo.getProfileImageUrl(),
                "KAKAO",
                String.valueOf(userInfo.getId()),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 구글 OAuth2 콜백
     *
     * 구글 인가 URL 예시:
     * https://accounts.google.com/o/oauth2/v2/auth
     *   ?client_id={CLIENT_ID}
     *   &redirect_uri=http://localhost:8080/api/v1/auth/callback/google
     *   &response_type=code
     *   &scope=openid email profile
     */
    @GetMapping("/callback/google")
    public ResponseEntity<TokenResponse> googleCallback(@RequestParam String code,
                                                        HttpServletRequest httpRequest) {
        String googleAccessToken = googleOAuthService.getAccessToken(code);
        GoogleUserInfo userInfo  = googleOAuthService.getUserInfo(googleAccessToken);

        TokenResponse response = authService.socialLogin(
                userInfo.getEmail(),
                userInfo.getName(),
                userInfo.getPicture(),
                "GOOGLE",
                userInfo.getSub(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // 토큰 재발급 / 로그아웃
    // -------------------------------------------------------------------------

    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        return ResponseEntity.ok(authService.reissue(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody ReissueRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
