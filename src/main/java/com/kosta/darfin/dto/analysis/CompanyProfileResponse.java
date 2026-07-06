package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

/**
 * 파이프라인이 이 3개 필드를 전용으로 만들지 않아 company_overview에서 최소한으로
 * 파생한다 (알려진 한계 — IMPLEMENTATION_PLAN.md 참고). governanceNotes는 아직
 * 생성 로직이 없어 빈 문자열.
 */
@Getter
@Builder
public class CompanyProfileResponse {
    private String businessDescription;
    private String shareStructure;
    private String governanceNotes;
}
