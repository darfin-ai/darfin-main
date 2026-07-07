package com.kosta.darfin.dto.payment;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TokenStatusResponse {
    private int tokenBalance;
    private int tokenQuota;
    private String planName;
    private LocalDateTime nextResetAt;
}
