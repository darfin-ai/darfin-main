package com.kosta.darfin.entity.payment;

import com.kosta.darfin.entity.common.Users;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Subscriptions.java
// planName: BASIC / PRO / ENTERPRISE
// status: ACTIVE / CANCEL_SCHEDULED(다음 결제일까지 이용 후 자동 해지) / PAST_DUE(결제 실패) / EXPIRED
@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor
public class Subscriptions {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private Users user;

    @Column(nullable = false, length = 20)
    private String planName;

    @Column(nullable = false, length = 20)
    private String status;

    private LocalDate nextPaymentDate;

    // 현재 결제 주기 시작일 (플랜 변경 시 일할 계산 기준)
    private LocalDate currentPeriodStart;

    // 다운그레이드 시 적립된 차액 크레딧, 다음 결제 청구 시 차감 후 초기화
    @Column(nullable = false)
    private Integer pendingCreditAmount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public Subscriptions(Users user, String planName, String status,
                          LocalDate nextPaymentDate, LocalDate currentPeriodStart) {
        this.user = user;
        this.planName = planName;
        this.status = status;
        this.nextPaymentDate = nextPaymentDate;
        this.currentPeriodStart = currentPeriodStart;
        this.pendingCreditAmount = 0;
    }

    public void changePlan(String newPlanName, LocalDate today) {
        this.planName = newPlanName;
        this.status = "ACTIVE";
        this.currentPeriodStart = today;
    }

    public void addPendingCredit(int amount) {
        this.pendingCreditAmount += amount;
    }

    public int consumePendingCredit() {
        int credit = this.pendingCreditAmount;
        this.pendingCreditAmount = 0;
        return credit;
    }

    public void scheduleCancel() {
        this.status = "CANCEL_SCHEDULED";
    }

    public void resumeCancel() {
        this.status = "ACTIVE";
    }

    // 결제 실패 시 상태 전환 + 익일 재시도를 위해 nextPaymentDate를 하루 뒤로 이동
    public void markPastDue(LocalDate retryDate) {
        this.status = "PAST_DUE";
        this.nextPaymentDate = retryDate;
    }

    public void renew(LocalDate nextPaymentDate, LocalDate periodStart) {
        this.status = "ACTIVE";
        this.nextPaymentDate = nextPaymentDate;
        this.currentPeriodStart = periodStart;
    }

    public void downgradeToBasic() {
        this.planName = "BASIC";
        this.status = "ACTIVE";
        this.nextPaymentDate = null;
        this.pendingCreditAmount = 0;
    }
}
