package com.kosta.darfin.service.disclosure;

import com.kosta.darfin.dto.disclosure.AnalysisItemDto;
import com.kosta.darfin.dto.disclosure.AnalysisResponseDto;
import com.kosta.darfin.entity.disclosure.AiAnalysisItem;
import com.kosta.darfin.entity.disclosure.Disclosure;
import com.kosta.darfin.repository.disclosure.AiAnalysisItemRepository;
import com.kosta.darfin.repository.disclosure.DisclosureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final AiAnalysisItemRepository analysisItemRepo;
    private final DisclosureRepository disclosureRepo;
    private final LlmPipelineClient llmClient;
    private final RiskNormalizer riskNormalizer;

    public AnalysisService(AiAnalysisItemRepository analysisItemRepo,
                            DisclosureRepository disclosureRepo,
                            LlmPipelineClient llmClient,
                            RiskNormalizer riskNormalizer) {
        this.analysisItemRepo = analysisItemRepo;
        this.disclosureRepo = disclosureRepo;
        this.llmClient = llmClient;
        this.riskNormalizer = riskNormalizer;
    }

    @Transactional
    public AnalysisResult analyzeAndSave(String rceptNo, String corpName, String dartFullText) {
        Disclosure disclosure = disclosureRepo.findById(rceptNo)
                .orElseThrow(() -> new IllegalArgumentException("disclosure 테이블에 없는 접수번호입니다: " + rceptNo));

        String typeCode = disclosure.getDisclosureType().getTypeCode();

        
        AnalysisResponseDto result = llmClient.requestAnalysis(typeCode, corpName, dartFullText);

        if (!result.isSuccess()) {
            log.error("[LLM 서비스 실패] {}", result.getErrorMessage());
            return AnalysisResult.error(result.getErrorMessage());
        }

        List<AnalysisItemDto> rawItems = result.getItems();
        if (rawItems == null || rawItems.isEmpty()) {
            return AnalysisResult.error("LLM 서비스가 분석 항목을 하나도 반환하지 않았습니다.");
        }

        if (result.isTruncated()) {
            log.warn("[Gemini 응답 잘림] rceptNo={} — 토큰 한도로 일부 항목만 복구됨(복구된 항목 수={})", rceptNo, rawItems.size());
        }

        
        String riskScaleCode;
        try {
            riskScaleCode = riskNormalizer.getRiskScaleCode(typeCode);
        } catch (IllegalArgumentException e) {
            log.error("[risk_scale_code 조회 실패] {}", e.getMessage());
            return AnalysisResult.error(e.getMessage());
        }

        List<AiAnalysisItem> toSave = new ArrayList<>();
        int dropped = result.getDroppedCount();
        Set<String> unresolvedLabels = new HashSet<>();

        for (int i = 0; i < rawItems.size(); i++) {
            AnalysisItemDto item = rawItems.get(i);

            
            if (item.getCharOffsetStart() < 0 || item.getCharOffsetEnd() < 0) {
                dropped++;
                continue;
            }

            Byte riskTier;
            try {
                riskTier = riskNormalizer.getRiskTier(riskScaleCode, item.getRiskLevel());
            } catch (RiskNormalizer.RiskLabelNotFoundException e) {
                unresolvedLabels.add(item.getRiskLevel());
                dropped++;
                continue;
            }

            AiAnalysisItem entity = new AiAnalysisItem();
            entity.setDisclosure(disclosure);
            entity.setItemNo((byte) toSave.size());
            entity.setCategory(item.getAnalysisCategory());
            entity.setTargetText(item.getTargetKey());
            entity.setCharStart(item.getCharOffsetStart());
            entity.setCharEnd(item.getCharOffsetEnd());
            entity.setMaterialImpact(item.getMaterialImpact());
            entity.setRiskLabel(item.getRiskLevel());
            entity.setRiskTier(riskTier);
            toSave.add(entity);
        }

        if (!unresolvedLabels.isEmpty()) {
            log.warn("[risk_scale 미등록 라벨로 드롭됨] riskScaleCode={} labels={}", riskScaleCode, unresolvedLabels);
        }

        if (toSave.isEmpty()) {
            return AnalysisResult.error("모든 분석 항목이 검증에 실패해 버려졌습니다 (dropped=" + dropped + ")");
        }

        
        analysisItemRepo.deleteByDisclosure_RceptNo(rceptNo);
        analysisItemRepo.saveAll(toSave);

        disclosure.setDroppedCount((short) dropped);
        disclosureRepo.save(disclosure);

        log.info("[저장 완료] rceptNo={} savedCount={} droppedCount={}", rceptNo, toSave.size(), dropped);

        return AnalysisResult.ok(toSave.size(), dropped);
    }

    public static class AnalysisResult {
        public final boolean success;
        public final int savedCount;
        public final int droppedCount;
        public final String errorMessage;

        private AnalysisResult(boolean success, int savedCount, int droppedCount, String errorMessage) {
            this.success = success;
            this.savedCount = savedCount;
            this.droppedCount = droppedCount;
            this.errorMessage = errorMessage;
        }

        public static AnalysisResult ok(int savedCount, int droppedCount) {
            return new AnalysisResult(true, savedCount, droppedCount, null);
        }

        public static AnalysisResult error(String message) {
            return new AnalysisResult(false, 0, 0, message);
        }
    }
}
