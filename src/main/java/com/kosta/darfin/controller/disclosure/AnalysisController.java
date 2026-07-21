package com.kosta.darfin.controller.disclosure;

import com.kosta.darfin.dto.disclosure.AnalysisRequestDto;
import com.kosta.darfin.service.disclosure.AnalysisService;
import com.kosta.darfin.service.disclosure.AnalysisService.AnalysisResult;
import com.kosta.darfin.service.payment.FeatureType;
import com.kosta.darfin.service.payment.TokenBillingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private static final int UNLOCK_TOKEN_COST = 2000;

    private final AnalysisService analysisService;
    private final TokenBillingService tokenBillingService;

    public AnalysisController(AnalysisService analysisService, TokenBillingService tokenBillingService) {
        this.analysisService = analysisService;
        this.tokenBillingService = tokenBillingService;
    }

    @PostMapping
    public ResponseEntity<?> analyze(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AnalysisRequestDto req) {
        if (req.getRceptNo() == null || req.getRceptNo().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "rceptNo가 비어 있습니다."));
        }
        if (req.getDartFullText() == null || req.getDartFullText().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "dartFullText가 비어 있습니다."));
        }

        // LLM 호출 전에 잔액만 미리 확인해 실패할 결제를 걸러내고, 실제 과금은 생성 성공 후에만 한다.
        tokenBillingService.assertSufficientBalance(userDetails.getUsername(), UNLOCK_TOKEN_COST);

        AnalysisResult result = analysisService.getOrGenerate(
                req.getRceptNo(), req.getCorpName(), req.getDartFullText());

        if (!result.success) {
            return ResponseEntity.internalServerError().body(Map.of("error", result.errorMessage));
        }

        // 공시 요약+분석은 문서(rceptNo)당 합쳐서 1회만 차감 — 이미 열람권이 있으면 재차감 없음
        tokenBillingService.chargeForUnlock(
                userDetails.getUsername(), FeatureType.DISCLOSURE, req.getRceptNo(), UNLOCK_TOKEN_COST);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("savedCount", result.savedCount);
        response.put("droppedCount", result.droppedCount);

        return ResponseEntity.ok(response);
    }
}
