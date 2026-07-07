package com.kosta.darfin.controller.payment;

import com.kosta.darfin.dto.payment.PaymentMethodResponse;
import com.kosta.darfin.dto.payment.RegisterCardRequest;
import com.kosta.darfin.entity.payment.PaymentMethods;
import com.kosta.darfin.service.payment.PaymentMethodService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/billing/methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @PostMapping
    public ResponseEntity<PaymentMethodResponse> registerCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RegisterCardRequest request) {
        PaymentMethods method = paymentMethodService.registerCard(
                userDetails.getUsername(), request.getAuthKey(), request.getCardName());
        return ResponseEntity.ok(toResponse(method));
    }

    @GetMapping
    public ResponseEntity<List<PaymentMethodResponse>> list(@AuthenticationPrincipal UserDetails userDetails) {
        List<PaymentMethodResponse> methods = paymentMethodService.list(userDetails.getUsername()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(methods);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        paymentMethodService.delete(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<Void> setDefault(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        paymentMethodService.setDefault(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    private PaymentMethodResponse toResponse(PaymentMethods method) {
        return PaymentMethodResponse.builder()
                .id(method.getId())
                .cardCompany(method.getCardCompany())
                .cardName(method.getCardName())
                .maskedCardNum(method.getMaskedCardNum())
                .isDefault(method.getIsDefault())
                .build();
    }
}
