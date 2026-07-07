package com.kosta.darfin.service.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 고정 3단계 요금제. 가격/토큰량/리셋 스케줄이 자주 바뀌지 않으므로 별도 관리 테이블 없이 enum으로 관리.
public enum PlanType {
    BASIC(0, 10000, false),
    PRO(15000, 30000, true),
    ENTERPRISE(49000, 50000, true);

    private final int price;
    private final int tokenQuota;
    private final boolean eveningReset; // true면 06시 외 18시에도 추가 초기화

    PlanType(int price, int tokenQuota, boolean eveningReset) {
        this.price = price;
        this.tokenQuota = tokenQuota;
        this.eveningReset = eveningReset;
    }

    public int getPrice() {
        return price;
    }

    public int getTokenQuota() {
        return tokenQuota;
    }

    public boolean isEveningReset() {
        return eveningReset;
    }

    public static PlanType fromName(String name) {
        try {
            return PlanType.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 요금제입니다: " + name);
        }
    }
}
