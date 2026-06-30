package com.kosta.darfin.controller;

import com.kosta.darfin.dto.user.ChangeNicknameRequest;
import com.kosta.darfin.dto.user.ChangePasswordRequest;
import com.kosta.darfin.dto.user.ChangeProfileImageRequest;
import com.kosta.darfin.dto.user.WithdrawRequest;
import com.kosta.darfin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/nickname")
    public ResponseEntity<Void> changeNickname(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangeNicknameRequest request) {
        userService.changeNickname(userDetails.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/profile-image")
    public ResponseEntity<Void> changeProfileImage(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangeProfileImageRequest request) {
        userService.changeProfileImage(userDetails.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/profile-image")
    public ResponseEntity<Void> deleteProfileImage(
            @AuthenticationPrincipal UserDetails userDetails) {
        userService.deleteProfileImage(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) WithdrawRequest request) {
        userService.withdraw(userDetails.getUsername(), request != null ? request : new WithdrawRequest());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/social")
    public ResponseEntity<Void> disconnectSocial(
            @AuthenticationPrincipal UserDetails userDetails) {
        userService.disconnectSocial(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
