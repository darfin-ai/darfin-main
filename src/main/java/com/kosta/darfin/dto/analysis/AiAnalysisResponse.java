package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/companies/{corpCode}/ai-analysis 응답.
 * 계약 사본: darfin-front/src/mocks/companyAnalysis/types.js (AiAnalysisResponse) —
 * 여기 바꾸면 그쪽도 같이 바꿀 것 (AGENTS.md 규칙).
 */
@Getter
@Builder
public class AiAnalysisResponse {

    /** locked | quant_only | generating_narrative | complete | failed | insufficient_data | preview */
    private String status;
    /** 실패 시 재시도 가능 여부. 기존 클라이언트에는 단순 추가 필드라 하위 호환된다. */
    private Boolean retryable;
    /** 원문 오류를 노출하지 않는 안정적인 오류 코드. */
    private String errorCode;
    /** status=locked일 때만 — 열람권 가격(토큰). 프론트 잠금 카드 CTA용. */
    private Integer unlockCost;
    /** 상태머신이 사용한 재무제표 구분 — CFS(연결), 연결 없으면 OFS(별도) 폴백. */
    private String fsDiv;
    /** true면 미온보딩 종목 — 계산/과금/큐 등록 없이 게이트만 알린다. */
    private boolean preview;
    /** 분기 라벨 오름차순 (예: 2024Q1 …). */
    private List<String> quarters;
    /** 최신 분기의 카테고리별 현재 상태 (프론트 카드 그리드). */
    private List<CategoryState> currentStates;
    /** 카테고리별 분기 상태 궤적 (트렌드 차트). */
    private List<CategoryTrajectory> trajectories;
    /** 분기별 결정론적 지표 스냅샷. */
    private List<QuarterMetricsEntry> metricsSeries;
    /** 도시에 이벤트 타임라인 (최신순). */
    private List<DossierEvent> dossierEvents;

    @Getter
    @Builder
    public static class CategoryState {
        private String category;
        private String state;
        private int consecutiveQtrs;
        /** LLM 생성 한국어 서사 — null이면 quant-only (프론트가 폴링). */
        private String narrativeKo;
        /** "차기 분기 확인 사항" — prescriptive 출력. */
        private String watchNextKo;
        /** 판정에 쓰인 정량 신호 스냅샷 (감사 가능성 — 출처 표시). */
        private Object signals;
    }

    @Getter
    @Builder
    public static class CategoryTrajectory {
        private String category;
        private List<TrajectoryPoint> points;
    }

    @Getter
    @Builder
    public static class TrajectoryPoint {
        private String quarter;
        private String state;
        private int consecutiveQtrs;
    }

    @Getter
    @Builder
    public static class QuarterMetricsEntry {
        private String quarter;
        private Map<String, Object> metrics;
    }

    @Getter
    @Builder
    public static class DossierEvent {
        private String rceptNo;
        private String eventType;
        private String category;
        private String itemKey;
        private Object detail;
        private String createdAt;
    }
}
