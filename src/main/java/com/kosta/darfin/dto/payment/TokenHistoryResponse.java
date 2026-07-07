package com.kosta.darfin.dto.payment;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TokenHistoryResponse {
    private String type;
    private String featureType;
    private String resourceId;
    private int amount;
    private int balanceAfter;
    private LocalDateTime createdAt;
}
