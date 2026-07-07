package com.kosta.darfin.dto.payment;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BillingHistoryResponse {
    private Long id;
    private String orderName;
    private int amount;
    private String status;
    private String receiptUrl;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
