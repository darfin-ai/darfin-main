package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Builder
public class CompanyDetailResponse {
    private CompanyResponse company;
    private List<ScoreComponentResponse> scores;
    /** 연결재무제표(is_consolidated=1) 기준 계정 시계열 */
    private List<FinancialMetricResponse> financials;
    /** 별도재무제표(is_consolidated=0) 기준 계정 시계열 */
    private List<FinancialMetricResponse> financialsSeparate;
    private List<FindingResponse> findings;
    private List<SectionDiffEntryResponse> diffs;
    private CompanyProfileResponse profile;
    private List<Object> mdnaHistory; // company_overview.overview_json.mdnaHistory 그대로 통과
    private List<RecentFilingResponse> recentFilings;
    private Object overview; // company_overview.overview_json 그대로 통과
    private DartOverviewResponse dartOverview; // DART 정기공시 API 기반 구조화 개요 (개요 탭)
    /** companies 미등록 stock-only preview (파이프라인·AI 데이터 없음) */
    private boolean preview;
    /** 요청 사용자의 AI 분석 열람권 보유 여부 — 컨트롤러가 채운다. */
    @Setter
    private boolean aiUnlocked;
}
