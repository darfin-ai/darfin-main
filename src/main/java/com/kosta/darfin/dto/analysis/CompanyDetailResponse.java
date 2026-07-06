package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CompanyDetailResponse {
    private CompanyResponse company;
    private List<ScoreComponentResponse> scores;
    private List<FinancialMetricResponse> financials;
    private List<FindingResponse> findings;
    private List<SectionDiffEntryResponse> diffs;
    private CompanyProfileResponse profile;
    private List<Object> strategyShifts; // 범위 밖 — 항상 빈 배열
    private List<RecentFilingResponse> recentFilings;
    private Object overview; // company_overview.overview_json 그대로 통과
}
