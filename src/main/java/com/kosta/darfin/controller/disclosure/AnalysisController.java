package com.kosta.darfin.controller.disclosure;

import com.kosta.darfin.dto.disclosure.AnalysisRequestDto;
import com.kosta.darfin.service.disclosure.AnalysisService;
import com.kosta.darfin.service.disclosure.AnalysisService.AnalysisResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }


    @PostMapping
    public ResponseEntity<?> analyze(@RequestBody AnalysisRequestDto req) {
        if (req.getRceptNo() == null || req.getRceptNo().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "rceptNo가 비어 있습니다."));
        }
        if (req.getDartFullText() == null || req.getDartFullText().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "dartFullText가 비어 있습니다."));
        }

        AnalysisResult result = analysisService.analyzeAndSave(
                req.getRceptNo(), req.getCorpName(), req.getDartFullText());

        if (!result.success) {
            return ResponseEntity.internalServerError().body(Map.of("error", result.errorMessage));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("savedCount", result.savedCount);
        response.put("droppedCount", result.droppedCount);

        return ResponseEntity.ok(response);
    }
}
