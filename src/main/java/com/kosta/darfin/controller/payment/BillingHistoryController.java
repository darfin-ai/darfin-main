package com.kosta.darfin.controller.payment;

import com.kosta.darfin.dto.payment.BillingHistoryResponse;
import com.kosta.darfin.dto.payment.RefundRequest;
import com.kosta.darfin.entity.payment.Payments;
import com.kosta.darfin.service.payment.BillingHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/billing/history")
@RequiredArgsConstructor
public class BillingHistoryController {

    private final BillingHistoryService billingHistoryService;

    @GetMapping
    public ResponseEntity<List<BillingHistoryResponse>> list(@AuthenticationPrincipal UserDetails userDetails) {
        List<BillingHistoryResponse> history = billingHistoryService.list(userDetails.getUsername()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<BillingHistoryResponse> getReceipt(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(billingHistoryService.getOne(userDetails.getUsername(), id)));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<BillingHistoryResponse> refund(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) RefundRequest request) {
        String reason = request != null && request.getReason() != null ? request.getReason() : "고객 요청";
        Payments payment = billingHistoryService.refund(userDetails.getUsername(), id, reason);
        return ResponseEntity.ok(toResponse(payment));
    }

    private BillingHistoryResponse toResponse(Payments payment) {
        return BillingHistoryResponse.builder()
                .id(payment.getId())
                .orderName(payment.getOrderName())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .receiptUrl(payment.getReceiptUrl())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
