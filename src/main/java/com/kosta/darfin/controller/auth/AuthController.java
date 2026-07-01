package com.kosta.darfin.controller.auth;

import com.kosta.darfin.dto.auth.FindIdRequest;
import com.kosta.darfin.dto.auth.FindIdResponse;
import com.kosta.darfin.dto.auth.LoginRequest;
import com.kosta.darfin.dto.auth.ReissueRequest;
import com.kosta.darfin.dto.auth.ResetPasswordRequest;
import com.kosta.darfin.dto.auth.SignupRequest;
import com.kosta.darfin.dto.auth.TokenResponse;
import com.kosta.darfin.dto.oauth.GoogleUserInfo;
import com.kosta.darfin.dto.oauth.KakaoUserInfo;
import com.kosta.darfin.service.auth.AuthService;
import com.kosta.darfin.service.oauth.GoogleOAuthService;
import com.kosta.darfin.service.oauth.KakaoOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    @Value("${oauth2.frontend-redirect-uri:http://localhost:5173/oauth/callback}")
    private String frontendRedirectUri;

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

    @PostMapping("/find-id")
    public ResponseEntity<List<FindIdResponse>> findId(@Valid @RequestBody FindIdRequest request) {
        return ResponseEntity.ok(authService.findId(request));
    }

    // -------------------------------------------------------------------------
    // 소셜 로그인 시작 (프론트 → 여기 → 카카오/구글 로그인 페이지)
    // -------------------------------------------------------------------------

    @GetMapping("/oauth2/authorize/kakao")
    public ResponseEntity<Void> kakaoAuthorize() {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(kakaoOAuthService.getAuthorizationUrl()));
        return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
    }

    @GetMapping("/oauth2/authorize/google")
    public ResponseEntity<Void> googleAuthorize() {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(googleOAuthService.getAuthorizationUrl()));
        return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
    }

    // -------------------------------------------------------------------------
    // 소셜 로그인 콜백
    // -------------------------------------------------------------------------

    @GetMapping("/callback/kakao")
    public ResponseEntity<Void> kakaoCallback(@RequestParam String code,
                                              HttpServletRequest httpRequest) {
        try {
            String kakaoAccessToken = kakaoOAuthService.getAccessToken(code);
            KakaoUserInfo userInfo  = kakaoOAuthService.getUserInfo(kakaoAccessToken);

            TokenResponse tokens = authService.socialLogin(
                    userInfo.getEmail(),
                    userInfo.getNickname(),
                    userInfo.getProfileImageUrl(),
                    "KAKAO",
                    String.valueOf(userInfo.getId()),
                    httpRequest.getRemoteAddr(),
                    httpRequest.getHeader("User-Agent")
            );
            return redirectToFrontend(tokens);
        } catch (ResponseStatusException e) {
            return redirectToFrontendError(e.getReason());
        }
    }

    @GetMapping("/callback/google")
    public ResponseEntity<Void> googleCallback(@RequestParam String code,
                                               HttpServletRequest httpRequest) {
        try {
            String googleAccessToken = googleOAuthService.getAccessToken(code);
            GoogleUserInfo userInfo  = googleOAuthService.getUserInfo(googleAccessToken);

            TokenResponse tokens = authService.socialLogin(
                    userInfo.getEmail(),
                    userInfo.getName(),
                    userInfo.getPicture(),
                    "GOOGLE",
                    userInfo.getSub(),
                    httpRequest.getRemoteAddr(),
                    httpRequest.getHeader("User-Agent")
            );
            return redirectToFrontend(tokens);
        } catch (ResponseStatusException e) {
            return redirectToFrontendError(e.getReason());
        }
    }

    private ResponseEntity<Void> redirectToFrontend(TokenResponse tokens) {
        String url = frontendRedirectUri
                + "?accessToken="  + URLEncoder.encode(tokens.getAccessToken(),  StandardCharsets.UTF_8)
                + "&refreshToken=" + URLEncoder.encode(tokens.getRefreshToken(), StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(url));
        return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
    }

    private ResponseEntity<Void> redirectToFrontendError(String errorMessage) {
        String url = frontendRedirectUri
                + "?error=" + URLEncoder.encode(errorMessage != null ? errorMessage : "오류가 발생했습니다.",
                                                StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(url));
        return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
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

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getEmail());
        return ResponseEntity.noContent().build();
    }
}
