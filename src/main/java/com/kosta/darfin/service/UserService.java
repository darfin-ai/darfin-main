package com.kosta.darfin.service;

import com.kosta.darfin.dto.user.ChangeNicknameRequest;
import com.kosta.darfin.dto.user.ChangePasswordRequest;
import com.kosta.darfin.dto.user.ChangeProfileImageRequest;
import com.kosta.darfin.dto.user.WithdrawRequest;
import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.repository.common.RefreshTokensRepository;
import com.kosta.darfin.repository.common.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UsersRepository usersRepository;
    private final RefreshTokensRepository refreshTokensRepository;
    private final PasswordEncoder passwordEncoder;

    // -------------------------------------------------------------------------
    // 닉네임 변경
    // -------------------------------------------------------------------------

    @Transactional
    public void changeNickname(String email, ChangeNicknameRequest request) {
        Users user = findActiveUser(email);
        user.updateNickname(request.getNickname());
    }

    // -------------------------------------------------------------------------
    // 비밀번호 변경
    // -------------------------------------------------------------------------

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        Users user = findActiveUser(email);

        if (!"LOCAL".equals(user.getProvider())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "소셜 로그인 계정은 비밀번호 변경을 지원하지 않습니다.");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호가 현재 비밀번호와 동일합니다.");
        }

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    // -------------------------------------------------------------------------
    // 프로필 사진 변경
    // -------------------------------------------------------------------------

    @Transactional
    public void changeProfileImage(String email, ChangeProfileImageRequest request) {
        Users user = findActiveUser(email);
        user.updateProfileImage(request.getImageUrl());
    }

    // -------------------------------------------------------------------------
    // 프로필 사진 삭제
    // -------------------------------------------------------------------------

    @Transactional
    public void deleteProfileImage(String email) {
        Users user = findActiveUser(email);
        user.updateProfileImage(null);
    }

    // -------------------------------------------------------------------------
    // 회원탈퇴
    // -------------------------------------------------------------------------

    @Transactional
    public void withdraw(String email, WithdrawRequest request) {
        Users user = findActiveUser(email);

        if ("LOCAL".equals(user.getProvider())) {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호를 입력해주세요.");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다.");
            }
        }

        refreshTokensRepository.deleteByUser(user);
        user.withdraw();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Users findActiveUser(String email) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if ("DELETED".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.GONE, "이미 탈퇴한 계정입니다.");
        }

        return user;
    }
}
