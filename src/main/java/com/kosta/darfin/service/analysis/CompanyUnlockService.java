package com.kosta.darfin.service.analysis;

import com.kosta.darfin.dto.analysis.AiUnlockResponse;
import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.repository.common.UsersRepository;
import com.kosta.darfin.service.payment.FeatureType;
import com.kosta.darfin.service.payment.TokenBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 분석 열람권 구매(unlock). 별표(관심 기업) 추가 + 온보딩 + 토큰 차감을
 * 한 트랜잭션으로 묶는다 — 잔액 부족(402) 시 별표/온보딩도 함께 롤백.
 * 별표 해제는 열람권에 영향을 주지 않는다 (user_content_unlocks는 독립 원장).
 */
@Service
@RequiredArgsConstructor
public class CompanyUnlockService {

    public static final int UNLOCK_TOKEN_COST = 2000;

    private final TokenBillingService tokenBillingService;
    private final StarredCompanyService starredCompanyService;
    private final UsersRepository usersRepository;

    @Transactional
    public AiUnlockResponse unlock(String email, String corpCode) {
        // corpCode 검증(404/400) + 관심 기업 등록 + 파이프라인 온보딩 (idempotent)
        starredCompanyService.addStarred(email, corpCode);

        boolean charged = tokenBillingService.chargeForUnlock(
                email, FeatureType.COMPANY, corpCode, UNLOCK_TOKEN_COST);

        int balance = usersRepository.findByEmail(email)
                .map(Users::getTokenBalance)
                .orElse(0);

        return AiUnlockResponse.builder()
                .unlocked(true)
                .alreadyUnlocked(!charged)
                .tokenBalance(balance)
                .build();
    }
}
