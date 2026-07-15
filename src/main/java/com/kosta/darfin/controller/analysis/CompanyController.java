package com.kosta.darfin.controller.analysis;

import com.kosta.darfin.dto.analysis.AiAnalysisResponse;
import com.kosta.darfin.dto.analysis.AiUnlockResponse;
import com.kosta.darfin.dto.analysis.CompanyDetailResponse;
import com.kosta.darfin.dto.analysis.CompanyListItemResponse;
import com.kosta.darfin.dto.analysis.CompanyOnboardResponse;
import com.kosta.darfin.dto.analysis.CompanySearchResultResponse;
import com.kosta.darfin.service.analysis.CompanyAnalysisService;
import com.kosta.darfin.service.analysis.CompanySearchService;
import com.kosta.darfin.service.analysis.CompanyUnlockService;
import com.kosta.darfin.service.analysis.RiskAnalysisService;
import com.kosta.darfin.service.payment.FeatureType;
import com.kosta.darfin.service.payment.TokenBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyAnalysisService companyAnalysisService;
    private final CompanySearchService companySearchService;
    private final RiskAnalysisService riskAnalysisService;
    private final TokenBillingService tokenBillingService;
    private final CompanyUnlockService companyUnlockService;

    /**
     * 기업 목록 + 최신 scores + changeSummary
     * GET /api/v1/companies
     */
    @GetMapping
    public ResponseEntity<List<CompanyListItemResponse>> listCompanies() {
        return ResponseEntity.ok(companyAnalysisService.listCompanies());
    }

    /**
     * 상장 종목 검색 (stock 테이블, companies 여부는 analyzed 플래그로 표시)
     * GET /api/v1/companies/search?keyword=
     */
    @GetMapping("/search")
    public ResponseEntity<List<CompanySearchResultResponse>> searchCompanies(
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(companySearchService.search(keyword));
    }

    /**
     * 분석 대상 등록 — companies에 없으면 stock 기준으로 INSERT
     * POST /api/v1/companies/{corpCode}/onboard
     */
    @PostMapping("/{corpCode}/onboard")
    public ResponseEntity<CompanyOnboardResponse> onboardCompany(@PathVariable String corpCode) {
        return ResponseEntity.ok(companySearchService.onboard(corpCode));
    }

    /**
     * 기업 상세 (개요/재무추이 탭) — 무료. 과금은 AI 분석 열람권(unlock)에서만 발생.
     * GET /api/v1/companies/{corpCode}
     */
    @GetMapping("/{corpCode}")
    public ResponseEntity<CompanyDetailResponse> getCompanyDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String corpCode) {
        CompanyDetailResponse detail = companyAnalysisService.getCompanyDetail(corpCode);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        detail.setAiUnlocked(tokenBillingService.isUnlocked(
                userDetails.getUsername(), FeatureType.COMPANY, corpCode));
        return ResponseEntity.ok(detail);
    }

    /**
     * AI 분석 열람권 구매 — 2000토큰 차감(1회), 관심 기업 자동 등록 + 온보딩.
     * 잔액 부족 시 402. 이미 열람권 보유 시 차감 없이 alreadyUnlocked=true.
     * POST /api/v1/companies/{corpCode}/ai-analysis/unlock
     */
    @PostMapping("/{corpCode}/ai-analysis/unlock")
    public ResponseEntity<AiUnlockResponse> unlockAiAnalysis(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String corpCode) {
        return ResponseEntity.ok(companyUnlockService.unlock(userDetails.getUsername(), corpCode));
    }

    /**
     * AI분석 탭 — 리스크 카테고리 상태/궤적/도시에 이벤트 (on-demand 계산+캐시).
     * GET /api/v1/companies/{corpCode}/ai-analysis
     * 열람권(user_content_unlocks) 미보유 시 계산·큐 등록 없이 locked 게이트 응답만
     * 준다 — 프론트가 잠금 카드(2000토큰 CTA)를 그린다.
     */
    @GetMapping("/{corpCode}/ai-analysis")
    public ResponseEntity<AiAnalysisResponse> getAiAnalysis(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String corpCode) {
        boolean unlocked = tokenBillingService.isUnlocked(
                userDetails.getUsername(), FeatureType.COMPANY, corpCode);
        if (!unlocked) {
            return ResponseEntity.ok(AiAnalysisResponse.builder()
                    .status("locked")
                    .unlockCost(CompanyUnlockService.UNLOCK_TOKEN_COST)
                    .preview(false)
                    .quarters(List.of()).currentStates(List.of()).trajectories(List.of())
                    .metricsSeries(List.of()).dossierEvents(List.of())
                    .build());
        }
        AiAnalysisResponse response = riskAnalysisService.getAiAnalysis(corpCode);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    /** 실패한 AI 내러티브 작업을 사용자가 명시적으로 재시도한다. */
    @PostMapping("/{corpCode}/ai-analysis/retry")
    public ResponseEntity<AiAnalysisResponse> retryAiAnalysis(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String corpCode) {
        boolean unlocked = tokenBillingService.isUnlocked(
                userDetails.getUsername(), FeatureType.COMPANY, corpCode);
        if (!unlocked) {
            return ResponseEntity.status(403).build();
        }
        AiAnalysisResponse response = riskAnalysisService.retryAiAnalysis(corpCode);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(response);
    }
}
