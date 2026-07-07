package com.kosta.darfin.dto.payment;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PlanResponse {
    private String planName;
    private int price;
    private int tokenQuota;
    private List<String> resetTimes;
}
