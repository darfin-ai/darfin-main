package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

/** POST /api/v1/companies/{corpCode}/ai-analysis/unlock 응답. */
@Getter
@Builder
public class AiUnlockResponse {
    private boolean unlocked;
    /** true면 이미 열람권 보유 — 토큰 차감 없음. */
    private boolean alreadyUnlocked;
    private int tokenBalance;
}
