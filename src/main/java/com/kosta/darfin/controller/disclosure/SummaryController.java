package com.kosta.darfin.controller.disclosure;

import com.kosta.darfin.dto.disclosure.SummaryRequestDto;
import com.kosta.darfin.dto.disclosure.SummaryResponseDto;
import com.kosta.darfin.service.disclosure.SummaryService;
import com.kosta.darfin.service.disclosure.SummaryService.SummaryResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @PostMapping
    public ResponseEntity<?> summarize(@RequestBody SummaryRequestDto req) {

        if (req.getDartContext() == null || req.getDartContext().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(SummaryResponseDto.error("dartContext가 비어 있습니다."));
        }

        SummaryResult result = summaryService.getOrGenerate(
                req.getRceptNo(), req.getCorpName(), req.getDartContext());

        if (!result.success) {
            return ResponseEntity.internalServerError().body(Map.of("error", result.errorMessage));
        }

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
