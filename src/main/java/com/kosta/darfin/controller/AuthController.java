package com.kosta.darfin.controller;

import com.kosta.darfin.dto.auth.FindIdRequest;
import com.kosta.darfin.dto.auth.FindIdResponse;
import com.kosta.darfin.dto.auth.LoginRequest;
import com.kosta.darfin.dto.auth.ReissueRequest;
import com.kosta.darfin.dto.auth.ResetPasswordRequest;
import com.kosta.darfin.dto.auth.SignupRequest;
import com.kosta.darfin.dto.auth.TokenResponse;
import com.kosta.darfin.dto.oauth.GoogleUserInfo;
import com.kosta.darfin.dto.oauth.KakaoUserInfo;
import com.kosta.darfin.service.AuthService;
import com.kosta.darfin.service.oauth.GoogleOAuthService;
import com.kosta.darfin.service.oauth.KakaoOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

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

    @PostMapping("/find-id")
    public ResponseEntity<List<FindIdResponse>> findId(@Valid @RequestBody FindIdRequest request) {
        return ResponseEntity.ok(authService.findId(request));
    }

    // -------------------------------------------------------------------------
    // 소셜 로그인 콜백
    // -------------------------------------------------------------------------

    @GetMapping("/callback/kakao")
    public ResponseEntity<TokenResponse> kakaoCallback(@RequestParam String code,
                                                       HttpServletRequest httpRequest) {
        String kakaoAccessToken = kakaoOAuthService.getAccessToken(code);
        KakaoUserInfo userInfo  = kakaoOAuthService.getUserInfo(kakaoAccessToken);

        return ResponseEntity.ok(authService.socialLogin(
                userInfo.getEmail(),
                userInfo.getNickname(),
                userInfo.getProfileImageUrl(),
                "KAKAO",
                String.valueOf(userInfo.getId()),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        ));
    }

    @GetMapping("/callback/google")
    public ResponseEntity<TokenResponse> googleCallback(@RequestParam String code,
                                                        HttpServletRequest httpRequest) {
        String googleAccessToken = googleOAuthService.getAccessToken(code);
        GoogleUserInfo userInfo  = googleOAuthService.getUserInfo(googleAccessToken);

        return ResponseEntity.ok(authService.socialLogin(
                userInfo.getEmail(),
                userInfo.getName(),
                userInfo.getPicture(),
                "GOOGLE",
                userInfo.getSub(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        ));
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
