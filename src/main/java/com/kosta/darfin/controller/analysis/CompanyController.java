package com.kosta.darfin.controller.analysis;

import com.kosta.darfin.dto.analysis.CompanyDetailResponse;
import com.kosta.darfin.dto.analysis.CompanyListItemResponse;
import com.kosta.darfin.service.analysis.CompanyAnalysisService;
import com.kosta.darfin.service.payment.FeatureType;
import com.kosta.darfin.service.payment.TokenBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private static final int UNLOCK_TOKEN_COST = 2000;

    private final CompanyAnalysisService companyAnalysisService;
    private final TokenBillingService tokenBillingService;

    /**
     * 기업 목록 + 최신 scores + changeSummary
     * GET /api/v1/companies
     */
    @GetMapping
    public ResponseEntity<List<CompanyListItemResponse>> listCompanies() {
        return ResponseEntity.ok(companyAnalysisService.listCompanies());
    }

    /**
     * 기업 상세 (개요/재무추이/공시변경 탭 데이터 전부)
     * GET /api/v1/companies/{corpCode}
     * AI 인사이트 준비 여부와 무관하게 기업 상세 최초 열람 시 열람권 차감(이후 재열람 무료)
     */
    @GetMapping("/{corpCode}")
    public ResponseEntity<CompanyDetailResponse> getCompanyDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String corpCode) {
        CompanyDetailResponse detail = companyAnalysisService.getCompanyDetail(corpCode);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        tokenBillingService.chargeForUnlock(
                userDetails.getUsername(), FeatureType.COMPANY, corpCode, UNLOCK_TOKEN_COST);
        return ResponseEntity.ok(detail);
    }
}
