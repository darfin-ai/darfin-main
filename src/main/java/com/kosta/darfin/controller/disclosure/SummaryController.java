package com.kosta.darfin.controller.disclosure;

import com.kosta.darfin.dto.disclosure.SummaryRequestDto;
import com.kosta.darfin.dto.disclosure.SummaryResponseDto;
import com.kosta.darfin.service.disclosure.SummaryService;
import com.kosta.darfin.service.disclosure.SummaryService.SummaryResult;
import com.kosta.darfin.service.payment.FeatureType;
import com.kosta.darfin.service.payment.TokenBillingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private static final int UNLOCK_TOKEN_COST = 2000;

    private final SummaryService summaryService;
    private final TokenBillingService tokenBillingService;

    public SummaryController(SummaryService summaryService, TokenBillingService tokenBillingService) {
        this.summaryService = summaryService;
        this.tokenBillingService = tokenBillingService;
    }

    @PostMapping
    public ResponseEntity<?> summarize(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody SummaryRequestDto req) {

        if (req.getDartContext() == null || req.getDartContext().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(SummaryResponseDto.error("dartContext가 비어 있습니다."));
        }

        // LLM 호출 전에 잔액만 미리 확인해 실패할 결제를 걸러내고, 실제 과금은 생성 성공 후에만 한다.
        tokenBillingService.assertSufficientBalance(userDetails.getUsername(), UNLOCK_TOKEN_COST);

        SummaryResult result = summaryService.getOrGenerate(
                req.getRceptNo(), req.getCorpName(), req.getDartContext());

        if (!result.success) {
            return ResponseEntity.internalServerError().body(Map.of("error", result.errorMessage));
        }

        // 공시 요약+분석은 문서(rceptNo)당 합쳐서 1회만 차감 — 이미 열람권이 있으면 재차감 없음
        tokenBillingService.chargeForUnlock(
                userDetails.getUsername(), FeatureType.DISCLOSURE, req.getRceptNo(), UNLOCK_TOKEN_COST);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("summaryText", result.summaryText);
        response.put("investorComment", result.investorComment);
        response.put("overallRisk", result.overallRisk);
        response.put("cacheHit", result.cacheHit);
        response.put("tokensIn", result.tokensIn);
        response.put("tokensOut", result.tokensOut);
        response.put("costUsd", result.costUsd);
        response.put("latencyMs", result.latencyMs);

        return ResponseEntity.ok(response);
    }
}
