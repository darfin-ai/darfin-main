package com.kosta.darfin.controller.payment;

import com.kosta.darfin.dto.payment.ChangePlanRequest;
import com.kosta.darfin.dto.payment.PlanResponse;
import com.kosta.darfin.dto.payment.SubscriptionResponse;
import com.kosta.darfin.entity.payment.Subscriptions;
import com.kosta.darfin.service.payment.PlanType;
import com.kosta.darfin.service.payment.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    public ResponseEntity<List<PlanResponse>> getPlans() {
        List<PlanResponse> plans = Arrays.stream(PlanType.values())
                .map(plan -> PlanResponse.builder()
                        .planName(plan.name())
                        .price(plan.getPrice())
                        .tokenQuota(plan.getTokenQuota())
                        .resetTimes(plan.isEveningReset() ? List.of("06:00", "18:00") : List.of("06:00"))
                        .build())
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/me")
    public ResponseEntity<SubscriptionResponse> getMySubscription(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(toResponse(subscriptionService.getMySubscription(userDetails.getUsername())));
    }

    @PostMapping("/change")
    public ResponseEntity<SubscriptionResponse> changePlan(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePlanRequest request) {
        PlanType target = PlanType.fromName(request.getPlanName());
        Subscriptions subscription = subscriptionService.changePlan(userDetails.getUsername(), target);
        return ResponseEntity.ok(toResponse(subscription));
    }

    @PostMapping("/cancel")
    public ResponseEntity<SubscriptionResponse> cancel(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(toResponse(subscriptionService.cancel(userDetails.getUsername())));
    }

    @PostMapping("/resume")
    public ResponseEntity<SubscriptionResponse> resume(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(toResponse(subscriptionService.resume(userDetails.getUsername())));
    }

    private SubscriptionResponse toResponse(Subscriptions subscription) {
        return SubscriptionResponse.builder()
                .planName(subscription.getPlanName())
                .status(subscription.getStatus())
                .nextPaymentDate(subscription.getNextPaymentDate())
                .pendingCreditAmount(subscription.getPendingCreditAmount())
                .build();
    }
}
