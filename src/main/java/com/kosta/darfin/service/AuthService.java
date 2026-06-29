package com.kosta.darfin.service;

import com.kosta.darfin.dto.auth.FindIdRequest;
import com.kosta.darfin.dto.auth.FindIdResponse;
import com.kosta.darfin.dto.auth.LoginRequest;
import com.kosta.darfin.dto.auth.SignupRequest;
import com.kosta.darfin.dto.auth.TokenResponse;
import com.kosta.darfin.entity.common.RefreshTokens;
import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.global.jwt.JwtTokenProvider;
import com.kosta.darfin.repository.common.RefreshTokensRepository;
import com.kosta.darfin.repository.common.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String TEMP_PW_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsersRepository usersRepository;
    private final RefreshTokensRepository refreshTokensRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    // -------------------------------------------------------------------------
    // 회원가입
    // -------------------------------------------------------------------------

    @Transactional
    public void signup(SignupRequest request) {
        if (usersRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");
        }

        Users user = Users.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .nickname(request.getNickname())
                .build();

        usersRepository.save(user);
    }

    // -------------------------------------------------------------------------
    // 아이디 찾기
    // -------------------------------------------------------------------------

    public List<FindIdResponse> findId(FindIdRequest request) {
        List<Users> users = usersRepository
                .findByNameAndPhoneOrderByCreatedAtAsc(request.getName(), request.getPhone());

        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "일치하는 계정을 찾을 수 없습니다. 이름과 전화번호를 확인해주세요.");
        }

        return users.stream()
                .map(u -> FindIdResponse.builder()
                        .email(u.getEmail())
                        .provider(u.getProvider())
                        .build())
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // 로그인
    // -------------------------------------------------------------------------

    @Transactional
    public TokenResponse login(LoginRequest request, String ipAddress, String userAgent) {
        Users user = usersRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

        if ("DELETED".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return issueTokens(user, ipAddress, userAgent);
    }

    // -------------------------------------------------------------------------
    // 소셜 로그인 (카카오 / 구글 공통)
    // -------------------------------------------------------------------------

    @Transactional
    public TokenResponse socialLogin(String email, String nickname, String profileImageUrl,
                                     String provider, String providerUserId,
                                     String ipAddress, String userAgent) {

        // 1순위: provider + providerUserId로 기존 소셜 계정 조회
        Users user = usersRepository
                .findByProviderAndProviderUserId(provider, providerUserId)
                .orElseGet(() ->
                    // 2순위: 같은 이메일의 LOCAL 계정이 있으면 소셜 계정 병합
                    usersRepository.findByEmail(email)
                            .orElseGet(() -> createSocialUser(email, nickname, profileImageUrl, provider, providerUserId))
                );

        return issueTokens(user, ipAddress, userAgent);
    }

    // -------------------------------------------------------------------------
    // 토큰 재발급 (RTR: Refresh Token Rotation)
    // -------------------------------------------------------------------------

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다.");
        }

        RefreshTokens saved = refreshTokensRepository.findByToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Refresh Token을 찾을 수 없습니다. 다시 로그인해주세요."));

        if (saved.getExpiredAt().isBefore(LocalDateTime.now())) {
            refreshTokensRepository.delete(saved);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "만료된 Refresh Token입니다. 다시 로그인해주세요.");
        }

        Users user = saved.getUser();
        String newAccessToken  = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        refreshTokensRepository.delete(saved);
        refreshTokensRepository.save(RefreshTokens.builder()
                .user(user)
                .token(newRefreshToken)
                .ipAddress(saved.getIpAddress())
                .userAgent(saved.getUserAgent())
                .expiredAt(LocalDateTime.now().plusWeeks(2))
                .build());

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .build();
    }

    // -------------------------------------------------------------------------
    // 로그아웃
    // -------------------------------------------------------------------------

    @Transactional
    public void logout(String refreshToken) {
        refreshTokensRepository.findByToken(refreshToken)
                .ifPresent(refreshTokensRepository::delete);
    }

    // -------------------------------------------------------------------------
    // 비밀번호 재설정
    // -------------------------------------------------------------------------

    @Transactional
    public void resetPassword(String email) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "등록된 이메일을 찾을 수 없습니다."));

        if (!"LOCAL".equals(user.getProvider())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "소셜 로그인 계정은 비밀번호 재설정을 지원하지 않습니다.");
        }

        String tempPassword = generateTempPassword();
        user.updatePassword(passwordEncoder.encode(tempPassword));
        mailService.sendTempPasswordMail(email, tempPassword);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(TEMP_PW_CHARS.charAt(SECURE_RANDOM.nextInt(TEMP_PW_CHARS.length())));
        }
        return sb.toString();
    }

    private Users createSocialUser(String email, String nickname, String profileImageUrl,
                                   String provider, String providerUserId) {
        Users user = Users.builder()
                .email(email)
                .nickname(nickname != null ? nickname : email.split("@")[0])
                .phone("")
                .profileImage(profileImageUrl)
                .provider(provider)
                .providerUserId(providerUserId)
                .build();
        return usersRepository.save(user);
    }

    private TokenResponse issueTokens(Users user, String ipAddress, String userAgent) {
        String accessToken  = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        refreshTokensRepository.deleteByUser(user);
        refreshTokensRepository.save(RefreshTokens.builder()
                .user(user)
                .token(refreshToken)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .expiredAt(LocalDateTime.now().plusWeeks(2))
                .build());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .build();
    }
}
