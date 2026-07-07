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
import com.kosta.darfin.service.auth.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 매일 새벽 1시 — 해지 예약된 구독을 Basic으로 전환하고, 결제일이 도래한 구독을 자동 청구한다.
 * 청구 실패 시 1회(익일) 재시도하며, 재시도까지 실패하면 Basic으로 강등 + 안내 메일을 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionBillingScheduler {

    private final SubscriptionsRepository subscriptionsRepository;
    private final PaymentMethodsRepository paymentMethodsRepository;
    private final PaymentsRepository paymentsRepository;
    private final TokenTransactionsRepository tokenTransactionsRepository;
    private final UsersRepository usersRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final MailService mailService;

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void runDailyBilling() {
        LocalDate today = LocalDate.now();

        List<Subscriptions> cancelDue = subscriptionsRepository.findByStatusAndNextPaymentDate("CANCEL_SCHEDULED", today);
        for (Subscriptions subscription : cancelDue) {
            try {
                downgradeToBasic(subscription);
            } catch (Exception e) {
                log.error("[SubscriptionBilling] 해지 처리 실패 subscriptionId={}", subscription.getId(), e);
            }
        }

        List<Subscriptions> billingDue = subscriptionsRepository
                .findByStatusInAndNextPaymentDate(List.of("ACTIVE", "PAST_DUE"), today);
        for (Subscriptions subscription : billingDue) {
            try {
                chargeSubscription(subscription, today);
            } catch (Exception e) {
                log.error("[SubscriptionBilling] 청구 처리 실패 subscriptionId={}", subscription.getId(), e);
            }
        }

        log.info("[SubscriptionBilling] 완료 - 해지 {}건, 청구 대상 {}건", cancelDue.size(), billingDue.size());
    }

    private void chargeSubscription(Subscriptions subscription, LocalDate today) {
        Long userId = subscription.getUser().getId();
        Users user = usersRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + userId));
        PlanType plan = safePlan(subscription.getPlanName());

        Optional<PaymentMethods> method = paymentMethodsRepository.findByUser_IdAndIsDefaultTrue(userId);
        if (method.isEmpty()) {
            handleFailure(subscription, user, "등록된 결제 수단이 없습니다.", today);
            return;
        }

        int amount = Math.max(0, plan.getPrice() - subscription.consumePendingCredit());
        String orderId = UUID.randomUUID().toString();
        String orderName = plan.name() + " 정기결제";
        Payments payment = paymentsRepository.save(Payments.builder()
                .user(user)
                .paymentMethod(method.get())
                .orderName(orderName)
                .amount(amount)
                .status("PENDING")
                .merchantUid(orderId)
                .build());

        try {
            TossPaymentsClient.ChargeResult result = tossPaymentsClient.chargeBilling(
                    method.get().getBillingKey(), "USER_" + userId, amount, orderId, orderName);
            payment.markDone(result.getPaymentKey(), result.getReceiptUrl(), LocalDateTime.now());

            subscription.renew(today.plusMonths(1), today);
            user.resetTokenBalance(plan.getTokenQuota());
            tokenTransactionsRepository.save(TokenTransactions.builder()
                    .user(user)
                    .type("RESET")
                    .featureType(FeatureType.RESET.name())
                    .amount(plan.getTokenQuota())
                    .balanceAfter(user.getTokenBalance())
                    .build());
        } catch (ResponseStatusException e) {
            payment.markFailed(e.getReason());
            handleFailure(subscription, user, e.getReason(), today);
        }
    }

    private void handleFailure(Subscriptions subscription, Users user, String reason, LocalDate today) {
        if ("PAST_DUE".equals(subscription.getStatus())) {
            // 2회 연속 실패 → Basic 강등
            subscription.downgradeToBasic();
            user.changeSubscriptionLevel("BASIC");
            user.resetTokenBalance(PlanType.BASIC.getTokenQuota());
            tokenTransactionsRepository.save(TokenTransactions.builder()
                    .user(user)
                    .type("RESET")
                    .featureType(FeatureType.RESET.name())
                    .amount(PlanType.BASIC.getTokenQuota())
                    .balanceAfter(user.getTokenBalance())
                    .build());
            try {
                mailService.sendSubscriptionDowngradedMail(user.getEmail(), reason);
            } catch (Exception e) {
                log.warn("[SubscriptionBilling] 강등 안내 메일 전송 실패 email={}", user.getEmail(), e);
            }
        } else {
            subscription.markPastDue(today.plusDays(1));
        }
    }

    private void downgradeToBasic(Subscriptions subscription) {
        Users user = usersRepository.findByIdForUpdate(subscription.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + subscription.getUser().getId()));
        subscription.downgradeToBasic();
        user.changeSubscriptionLevel("BASIC");
        user.resetTokenBalance(PlanType.BASIC.getTokenQuota());
        tokenTransactionsRepository.save(TokenTransactions.builder()
                .user(user)
                .type("RESET")
                .featureType(FeatureType.RESET.name())
                .amount(PlanType.BASIC.getTokenQuota())
                .balanceAfter(user.getTokenBalance())
                .build());
    }

    private PlanType safePlan(String planName) {
        try {
            return PlanType.valueOf(planName);
        } catch (IllegalArgumentException | NullPointerException e) {
            return PlanType.BASIC;
        }
    }
}
