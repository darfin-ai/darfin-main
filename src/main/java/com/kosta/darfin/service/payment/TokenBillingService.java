package com.kosta.darfin.service.payment;

import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.entity.payment.TokenTransactions;
import com.kosta.darfin.entity.payment.UserContentUnlocks;
import com.kosta.darfin.repository.common.UsersRepository;
import com.kosta.darfin.repository.payment.TokenTransactionsRepository;
import com.kosta.darfin.repository.payment.UserContentUnlocksRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenBillingService {

    private final UsersRepository usersRepository;
    private final TokenTransactionsRepository tokenTransactionsRepository;
    private final UserContentUnlocksRepository userContentUnlocksRepository;

    /**
     * 공시요약·분석/기업분석처럼 "리소스 최초 열람 시 1회만 차감"하는 과금.
     * 이미 열람권이 있으면 차감 없이 false 반환 — 호출부는 그대로 콘텐츠를 서빙하면 된다.
     */
    @Transactional
    public boolean chargeForUnlock(String email, FeatureType featureType, String resourceId, int amount) {
        Users user = resolveAuthenticatedUser(email);
        boolean alreadyUnlocked = userContentUnlocksRepository
                .existsByUser_IdAndFeatureTypeAndResourceId(user.getId(), featureType.name(), resourceId);
        if (alreadyUnlocked) {
            return false;
        }

        Users locked = deduct(user.getId(), featureType, resourceId, amount);
        userContentUnlocksRepository.save(UserContentUnlocks.builder()
                .user(locked)
                .featureType(featureType.name())
                .resourceId(resourceId)
                .build());
        return true;
    }

    /** 투자분석 리포트 생성처럼 매번 차감하는 과금(dedup 없음). */
    @Transactional
    public void chargeForAction(String email, FeatureType featureType, int amount) {
        Users user = resolveAuthenticatedUser(email);
        deduct(user.getId(), featureType, null, amount);
    }

    // 비용이 큰 외부 호출(LLM 등) 전에 잔액만 미리 확인하고 싶을 때 사용
    @Transactional(readOnly = true)
    public void assertSufficientBalance(String email, int amount) {
        Users user = resolveAuthenticatedUser(email);
        if (user.getTokenBalance() < amount) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "토큰이 부족합니다.");
        }
    }

    @Transactional(readOnly = true)
    public Users getTokenStatus(String email) {
        return resolveAuthenticatedUser(email);
    }

    @Transactional(readOnly = true)
    public List<TokenTransactions> getHistory(String email) {
        Users user = resolveAuthenticatedUser(email);
        return tokenTransactionsRepository.findTop50ByUser_IdOrderByCreatedAtDesc(user.getId());
    }

    private Users deduct(Long userId, FeatureType featureType, String resourceId, int amount) {
        Users user = usersRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (!user.deductToken(amount)) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "토큰이 부족합니다. 보유: " + user.getTokenBalance() + ", 필요: " + amount);
        }

        tokenTransactionsRepository.save(TokenTransactions.builder()
                .user(user)
                .type("CHARGE")
                .featureType(featureType.name())
                .resourceId(resourceId)
                .amount(-amount)
                .balanceAfter(user.getTokenBalance())
                .build());

        return user;
    }

    private Users resolveAuthenticatedUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }
        return usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다."));
    }
}
