package com.kosta.darfin.service.payment;

import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.entity.payment.PaymentMethods;
import com.kosta.darfin.entity.payment.Payments;
import com.kosta.darfin.entity.payment.Subscriptions;
import com.kosta.darfin.entity.payment.TokenTransactions;
import com.kosta.darfin.repository.common.UsersRepository;
import com.kosta.darfin.repository.payment.PaymentMethodsRepository;
import com.kosta.darfin.repository.payment.PaymentsRepository;
import com.kosta.darfin.repository.payment.SubscriptionsRepository;
import com.kosta.darfin.repository.payment.TokenTransactionsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionsRepository subscriptionsRepository;
    private final PaymentMethodsRepository paymentMethodsRepository;
    private final PaymentsRepository paymentsRepository;
    private final TokenTransactionsRepository tokenTransactionsRepository;
    private final UsersRepository usersRepository;
    private final TossPaymentsClient tossPaymentsClient;

    @Transactional(readOnly = true)
    public Subscriptions getMySubscription(String email) {
        return findSubscription(resolveAuthenticatedUser(email).getId());
    }

    /**
     * BASIC → PRO/ENTERPRISE(신규 결제 시작) 또는 PRO ↔ ENTERPRISE(일할 차액 정산) 변경.
     * 즉시 적용: 업그레이드는 차액을 바로 청구하고, 다운그레이드는 차액을 크레딧으로 적립해
     * 다음 정기결제에서 차감한다. BASIC으로의 다운그레이드는 이 메서드가 아니라 cancel()로 처리한다
     * (다음 결제일까지 이용 후 자동 전환 정책과 분리하기 위함).
     */
    @Transactional
    public Subscriptions changePlan(String email, PlanType target) {
        if (target == PlanType.BASIC) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "BASIC으로 변경하려면 구독 해지를 이용해주세요.");
        }

        Users user = resolveAuthenticatedUser(email);
        Long userId = user.getId();
        Subscriptions subscription = findSubscription(userId);
        PlanType current = PlanType.fromName(subscription.getPlanName());
        if (current == target) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 이용 중인 요금제입니다.");
        }

        PaymentMethods method = paymentMethodsRepository.findByUser_IdAndIsDefaultTrue(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "등록된 결제 수단이 없습니다. 카드를 먼저 등록해주세요."));

        LocalDate today = LocalDate.now();
        boolean isNewSubscription = current == PlanType.BASIC;
        int chargeAmount = isNewSubscription
                ? target.getPrice()
                : prorate(target.getPrice() - current.getPrice(),
                        subscription.getCurrentPeriodStart(), subscription.getNextPaymentDate(), today);

        // 잔액 변경(플랜/토큰)까지 하나의 트랜잭션에서 다루므로 락을 걸어 동시 변경을 직렬화
        Users lockedUser = usersRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (chargeAmount > 0) {
            chargeNow(lockedUser, method, chargeAmount, target.name() + " 플랜 변경");
        } else if (chargeAmount < 0) {
            subscription.addPendingCredit(-chargeAmount);
        }

        subscription.changePlan(target.name(), isNewSubscription ? today : subscription.getCurrentPeriodStart());
        if (isNewSubscription) {
            subscription.renew(today.plusMonths(1), today);
        }

        lockedUser.changeSubscriptionLevel(target.name());
        lockedUser.resetTokenBalance(target.getTokenQuota());
        tokenTransactionsRepository.save(TokenTransactions.builder()
                .user(lockedUser)
                .type("PLAN_CHANGE")
                .featureType(FeatureType.RESET.name())
                .amount(target.getTokenQuota())
                .balanceAfter(lockedUser.getTokenBalance())
                .build());

        return subscription;
    }

    /** 다음 결제일까지 이용 후 자동으로 BASIC 전환(즉시 해지 아님). */
    @Transactional
    public Subscriptions cancel(String email) {
        Subscriptions subscription = findSubscription(resolveAuthenticatedUser(email).getId());
        if (PlanType.fromName(subscription.getPlanName()) == PlanType.BASIC) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 무료(BASIC) 요금제입니다.");
        }
        if ("CANCEL_SCHEDULED".equals(subscription.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 해지 예약된 구독입니다.");
        }
        subscription.scheduleCancel();
        return subscription;
    }

    /** 해지 예약 취소(계속 이용). */
    @Transactional
    public Subscriptions resume(String email) {
        Subscriptions subscription = findSubscription(resolveAuthenticatedUser(email).getId());
        if (!"CANCEL_SCHEDULED".equals(subscription.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해지 예약 상태가 아닙니다.");
        }
        subscription.resumeCancel();
        return subscription;
    }

    private Subscriptions findSubscription(Long userId) {
        return subscriptionsRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "구독 정보를 찾을 수 없습니다."));
    }

    private Users resolveAuthenticatedUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }
        return usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다."));
    }

    private void chargeNow(Users user, PaymentMethods method, int amount, String orderName) {
        String orderId = UUID.randomUUID().toString();
        Payments payment = paymentsRepository.save(Payments.builder()
                .user(user)
                .paymentMethod(method)
                .orderName(orderName)
                .amount(amount)
                .status("PENDING")
                .merchantUid(orderId)
                .build());

        TossPaymentsClient.ChargeResult result = tossPaymentsClient.chargeBilling(
                method.getBillingKey(), "USER_" + user.getId(), amount, orderId, orderName);

        payment.markDone(result.getPaymentKey(), result.getReceiptUrl(), LocalDateTime.now());
    }

    // 남은 기간 비율만큼 차액을 일할 계산. 기준 기간 정보가 없으면(신규 구독 등) 차액 전액 반영.
    private int prorate(int priceDiff, LocalDate periodStart, LocalDate periodEnd, LocalDate today) {
        if (priceDiff == 0 || periodStart == null || periodEnd == null) {
            return priceDiff;
        }
        long totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
        if (totalDays <= 0) {
            return priceDiff;
        }
        long remainingDays = Math.max(0, Math.min(ChronoUnit.DAYS.between(today, periodEnd), totalDays));
        return (int) Math.round(priceDiff * (remainingDays / (double) totalDays));
    }
}
