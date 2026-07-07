package com.kosta.darfin.dto.payment;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class SubscriptionResponse {
    private String planName;
    private String status;
    private LocalDate nextPaymentDate;
    private int pendingCreditAmount;
}
