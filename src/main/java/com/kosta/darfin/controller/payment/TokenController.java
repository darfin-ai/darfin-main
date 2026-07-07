package com.kosta.darfin.controller.payment;

import com.kosta.darfin.dto.payment.TokenHistoryResponse;
import com.kosta.darfin.dto.payment.TokenStatusResponse;
import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.service.payment.PlanType;
import com.kosta.darfin.service.payment.TokenBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tokens")
@RequiredArgsConstructor
public class TokenController {

    private final TokenBillingService tokenBillingService;

    @GetMapping("/me")
    public ResponseEntity<TokenStatusResponse> getMyTokenStatus(@AuthenticationPrincipal UserDetails userDetails) {
        Users user = tokenBillingService.getTokenStatus(userDetails.getUsername());
        PlanType plan = safePlan(user.getSubscriptionLevel());
        return ResponseEntity.ok(TokenStatusResponse.builder()
                .tokenBalance(user.getTokenBalance())
                .tokenQuota(plan.getTokenQuota())
                .planName(plan.name())
                .nextResetAt(computeNextResetAt(plan))
                .build());
    }

    @GetMapping("/history")
    public ResponseEntity<List<TokenHistoryResponse>> getHistory(@AuthenticationPrincipal UserDetails userDetails) {
        List<TokenHistoryResponse> history = tokenBillingService.getHistory(userDetails.getUsername()).stream()
                .map(tx -> TokenHistoryResponse.builder()
                        .type(tx.getType())
                        .featureType(tx.getFeatureType())
                        .resourceId(tx.getResourceId())
                        .amount(tx.getAmount())
                        .balanceAfter(tx.getBalanceAfter())
                        .createdAt(tx.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    private PlanType safePlan(String subscriptionLevel) {
        try {
            return PlanType.valueOf(subscriptionLevel);
        } catch (IllegalArgumentException | NullPointerException e) {
            return PlanType.BASIC;
        }
    }

    private LocalDateTime computeNextResetAt(PlanType plan) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today6 = now.toLocalDate().atTime(6, 0);
        LocalDateTime today18 = now.toLocalDate().atTime(18, 0);

        if (now.isBefore(today6)) {
            return today6;
        }
        if (plan.isEveningReset() && now.isBefore(today18)) {
            return today18;
        }
        return today6.plusDays(1);
    }
}
