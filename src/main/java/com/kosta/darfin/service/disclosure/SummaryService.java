package com.kosta.darfin.service.disclosure;

import com.kosta.darfin.dto.disclosure.SummaryResponseDto;
import com.kosta.darfin.entity.disclosure.AiSummaryResult;
import com.kosta.darfin.entity.disclosure.Disclosure;
import com.kosta.darfin.repository.disclosure.AiSummaryResultRepository;
import com.kosta.darfin.repository.disclosure.DisclosureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;


@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);
    private static final String UNKNOWN_MODEL_NAME = "unknown";

    private final AiSummaryResultRepository summaryRepo;
    private final DisclosureRepository disclosureRepo;
    private final LlmPipelineClient llmClient;
    private final RiskNormalizer riskNormalizer;

    public SummaryService(AiSummaryResultRepository summaryRepo,
                           DisclosureRepository disclosureRepo,
                           LlmPipelineClient llmClient,
                           RiskNormalizer riskNormalizer) {
        this.summaryRepo = summaryRepo;
        this.disclosureRepo = disclosureRepo;
        this.llmClient = llmClient;
        this.riskNormalizer = riskNormalizer;
    }

    @Transactional
    public SummaryResult getOrGenerate(String rceptNo, String corpName, String dartContext) {
        Optional<AiSummaryResult> existing = summaryRepo.findById(rceptNo);

        if (existing.isPresent() && existing.get().getErrorCode() == null) {
            AiSummaryResult summary = existing.get();
            log.info("[DB HIT] rceptNo={}", rceptNo);
            return SummaryResult.hit(
                    summary.getSummaryText(), summary.getInvestorComment(), summary.getRiskLabel(),
                    summary.getTokensIn(), summary.getTokensOut(),
                    summary.getCostUsd() == null ? null : summary.getCostUsd().doubleValue(),
                    summary.getLatencyMs());
        }

        log.info("[DB MISS] LLM 서비스 호출 rceptNo={}", rceptNo);
        return generateAndSave(rceptNo, corpName, dartContext);
    }

    @Transactional
    public SummaryResult generateAndSave(String rceptNo, String corpName, String dartContext) {
        Disclosure disclosure = disclosureRepo.findById(rceptNo)
                .orElseThrow(() -> new IllegalArgumentException("disclosure 테이블에 없는 접수번호입니다: " + rceptNo));

        String typeCode = disclosure.getDisclosureType().getTypeCode();

        
        SummaryResponseDto result = llmClient.requestSummary(typeCode, corpName, dartContext);

        if (!result.isSuccess()) {
            log.error("[LLM 서비스 실패] {}", result.getErrorMessage());
            saveFailure(rceptNo, "GEMINI_TIMEOUT", result.getErrorMessage(), result.getModelName(), dartContext);
            return SummaryResult.error(result.getErrorMessage());
        }

        String rawRiskLabel = result.getOverallRisk();


        RiskNormalizer.NormalizedRisk normalized;
        try {
            normalized = riskNormalizer.normalize(typeCode, rawRiskLabel);
        } catch (RiskNormalizer.RiskLabelNotFoundException e) {
            log.error("[risk_tier 비정규화 실패] {}", e.getMessage());
            saveFailure(rceptNo, "RISK_LABEL_NOT_FOUND", e.getMessage(), result.getModelName(), dartContext);
            return SummaryResult.error(e.getMessage());
        }

        double costUsd = estimateCostUsd(result.getTokensIn(), result.getTokensOut(), result.getModelName());

        AiSummaryResult summary = new AiSummaryResult();
        summary.setRceptNo(rceptNo);
        summary.setSummaryText(result.getSummaryText());
        summary.setInvestorComment(result.getInvestorComment());
        summary.setRiskLabel(normalized.riskLabel);
        summary.setRiskTier(normalized.riskTier);
        summary.setModelName(resolveModelName(result.getModelName()));
        summary.setTokensIn(result.getTokensIn());
        summary.setTokensOut(result.getTokensOut());
        summary.setCostUsd(BigDecimal.valueOf(costUsd));
        summary.setLatencyMs((int) result.getLatencyMs());
        summary.setCacheHit(false);
        summary.setCompressedContextChars(dartContext == null ? null : dartContext.length());

        summaryRepo.save(summary);

        log.info("[저장 완료] rceptNo={} riskLabel={} riskTier={}", rceptNo, normalized.riskLabel, normalized.riskTier);

        return SummaryResult.miss(
                result.getSummaryText(), result.getInvestorComment(), normalized.riskLabel,
                result.getTokensIn(), result.getTokensOut(), costUsd, (int) result.getLatencyMs());
    }

    private void saveFailure(String rceptNo, String errorCode, String errorMessage, String modelName, String dartContext) {
        AiSummaryResult summary = new AiSummaryResult();
        summary.setRceptNo(rceptNo);
        summary.setSummaryText("");
        summary.setInvestorComment("");
        summary.setRiskLabel("Neutral");
        summary.setRiskTier((byte) 3);
        summary.setModelName(resolveModelName(modelName));
        summary.setCacheHit(false);
        summary.setErrorCode(errorCode);
        summary.setErrorMessage(errorMessage == null ? null : errorMessage.substring(0, Math.min(300, errorMessage.length())));
        summary.setCompressedContextChars(dartContext == null ? null : dartContext.length());
        summaryRepo.save(summary);
    }

    private String resolveModelName(String modelName) {
        return modelName != null ? modelName : UNKNOWN_MODEL_NAME;
    }

    private static final Map<String, double[]> MODEL_PRICING_PER_MILLION_TOKENS = Map.of(
            "gemini-2.5-flash", new double[]{0.30, 2.50},
            "gemini-2.5-flash-lite", new double[]{0.10, 0.40}
    );


    private static final double[] DEFAULT_PRICING = MODEL_PRICING_PER_MILLION_TOKENS.get("gemini-2.5-flash-lite");

    private double estimateCostUsd(Integer tokensIn, Integer tokensOut, String modelName) {
        if (tokensIn == null || tokensOut == null) return 0.0;
        double[] pricing = MODEL_PRICING_PER_MILLION_TOKENS.getOrDefault(modelName, DEFAULT_PRICING);
        double cost = (tokensIn / 1_000_000.0) * pricing[0] + (tokensOut / 1_000_000.0) * pricing[1];
        return Math.round(cost * 1_000_000.0) / 1_000_000.0;
    }

    public static class SummaryResult {
        public final boolean cacheHit;
        public final boolean success;
        public final String summaryText;
        public final String investorComment;
        public final String overallRisk;
        public final String errorMessage;
        public final Integer tokensIn;
        public final Integer tokensOut;
        public final Double costUsd;
        public final Integer latencyMs;

        private SummaryResult(boolean cacheHit, boolean success, String summaryText, String investorComment,
                               String overallRisk, String errorMessage, Integer tokensIn, Integer tokensOut,
                               Double costUsd, Integer latencyMs) {
            this.cacheHit = cacheHit;
            this.success = success;
            this.summaryText = summaryText;
            this.investorComment = investorComment;
            this.overallRisk = overallRisk;
            this.errorMessage = errorMessage;
            this.tokensIn = tokensIn;
            this.tokensOut = tokensOut;
            this.costUsd = costUsd;
            this.latencyMs = latencyMs;
        }

        public static SummaryResult hit(String s, String i, String r, Integer ti, Integer to, Double cost, Integer latency) {
            return new SummaryResult(true, true, s, i, r, null, ti, to, cost, latency);
        }

        public static SummaryResult miss(String s, String i, String r, Integer ti, Integer to, Double cost, Integer latency) {
            return new SummaryResult(false, true, s, i, r, null, ti, to, cost, latency);
        }

        public static SummaryResult error(String msg) {
            return new SummaryResult(false, false, null, null, null, msg, 0, 0, 0.0, 0);
        }
    }
}
