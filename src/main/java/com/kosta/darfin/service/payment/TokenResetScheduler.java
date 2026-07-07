package com.kosta.darfin.service.payment;

import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.entity.payment.TokenTransactions;
import com.kosta.darfin.repository.common.UsersRepository;
import com.kosta.darfin.repository.payment.TokenTransactionsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenResetScheduler {

    private final UsersRepository usersRepository;
    private final TokenTransactionsRepository tokenTransactionsRepository;

    // 매일 06:00 — 전체 사용자 토큰 초기화 (Basic/Pro/Enterprise 공통)
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void resetAll() {
        List<Users> users = usersRepository.findAll();
        for (Users user : users) {
            resetOne(user, safePlan(user.getSubscriptionLevel()));
        }
        log.info("[TokenReset] 06:00 전체 초기화 완료 - {}명", users.size());
    }

    // 매일 18:00 — Pro/Enterprise 추가 초기화 (일 2회 리셋)
    @Scheduled(cron = "0 0 18 * * *")
    @Transactional
    public void resetEveningPlans() {
        List<Users> users = usersRepository.findBySubscriptionLevelIn(List.of("PRO", "ENTERPRISE"));
        for (Users user : users) {
            resetOne(user, safePlan(user.getSubscriptionLevel()));
        }
        log.info("[TokenReset] 18:00 Pro/Enterprise 초기화 완료 - {}명", users.size());
    }

    private void resetOne(Users user, PlanType plan) {
        user.resetTokenBalance(plan.getTokenQuota());
        tokenTransactionsRepository.save(TokenTransactions.builder()
                .user(user)
                .type("RESET")
                .featureType(FeatureType.RESET.name())
                .amount(plan.getTokenQuota())
                .balanceAfter(user.getTokenBalance())
                .build());
    }

    private PlanType safePlan(String subscriptionLevel) {
        try {
            return PlanType.valueOf(subscriptionLevel);
        } catch (IllegalArgumentException | NullPointerException e) {
            return PlanType.BASIC;
        }
    }
}
