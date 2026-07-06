package com.kosta.darfin.service.disclosure;

import com.kosta.darfin.dto.disclosure.AnalysisItemDto;
import com.kosta.darfin.dto.disclosure.AnalysisResponseDto;
import com.kosta.darfin.dto.disclosure.SummaryResponseDto;
import com.kosta.darfin.dto.disclosure.TermHighlightDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
public class LlmPipelineClient {

    private static final Logger log = LoggerFactory.getLogger(LlmPipelineClient.class);

    @Value("${llm.service.base-url:http://127.0.0.1:8002}")
    private String llmServiceBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LlmPipelineClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public SummaryResponseDto requestSummary(String typeCode, String corpName, String dartContext) {
        String url = llmServiceBaseUrl + "/llm/summary";

        Map<String, Object> body = Map.of(
                "typeCode", typeCode,
                "corpName", corpName == null ? "" : corpName,
                "dartContext", dartContext
        );

        try {
            ResponseEntity<String> res = restTemplate.postForEntity(url, withJsonHeaders(body), String.class);
            return parseSummaryResponse(res.getBody());
        } catch (Exception e) {
            log.error("LLM 서비스(요약) 호출 실패", e);
            return SummaryResponseDto.error("LLM 서비스 호출 실패: " + e.getMessage());
        }
    }

    
    public AnalysisResponseDto requestAnalysis(String typeCode, String corpName, String dartFullText) {
        String url = llmServiceBaseUrl + "/llm/analysis";

        Map<String, Object> body = Map.of(
                "typeCode", typeCode,
                "corpName", corpName == null ? "" : corpName,
                "dartFullText", dartFullText
        );

        try {
            ResponseEntity<String> res = restTemplate.postForEntity(url, withJsonHeaders(body), String.class);
            return parseAnalysisResponse(res.getBody());
        } catch (Exception e) {
            log.error("LLM 서비스(분석) 호출 실패", e);
            return AnalysisResponseDto.error("LLM 서비스 호출 실패: " + e.getMessage());
        }
    }

    /**
     * 공시 원문을 Python 용어사전 서비스(/glossary/terms)로 보내 등록된 전문용어의
     * 위치(startIndex/endIndex)를 받아온다. 용어 마스터 데이터는 Python 쪽
     * app/data/dictionary_terms.json 파일로 관리되며, 이 서비스는 DB에 저장하지 않고
     * 매 호출마다 새로 계산한다.
     */
    public List<TermHighlightDto> requestTermHighlights(String originalText) {
        String url = llmServiceBaseUrl + "/glossary/terms";

        Map<String, Object> body = Map.of("originalText", originalText);

        try {
            ResponseEntity<String> res = restTemplate.postForEntity(url, withJsonHeaders(body), String.class);
            return parseGlossaryResponse(res.getBody());
        } catch (Exception e) {
            log.error("LLM 서비스(용어사전) 호출 실패", e);
            return List.of();
        }
    }

    private List<TermHighlightDto> parseGlossaryResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        boolean success = root.path("success").asBoolean(false);
        if (!success) {
            return List.of();
        }

        List<TermHighlightDto> highlights = new ArrayList<>();
        for (JsonNode node : root.path("terms")) {
            highlights.add(new TermHighlightDto(
                    node.path("termId").asLong(),
                    node.path("term").asText(""),
                    node.path("category").asText(""),
                    node.path("definition").asText(""),
                    node.path("startIndex").asInt(-1),
                    node.path("endIndex").asInt(-1)
            ));
        }
        return highlights;
    }

    private HttpEntity<Map<String, Object>> withJsonHeaders(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private SummaryResponseDto parseSummaryResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        boolean success = root.path("success").asBoolean(false);
        String modelName = root.hasNonNull("modelName") ? root.path("modelName").asText() : null;
        if (!success) {
            return SummaryResponseDto.error(root.path("errorMessage").asText("LLM 서비스 오류"), modelName);
        }

        return SummaryResponseDto.ok(
                root.path("summaryText").asText(""),
                root.path("investorComment").asText(""),
                root.path("overallRisk").asText("Neutral"),
                modelName,
                root.path("tokensIn").asInt(0),
                root.path("tokensOut").asInt(0),
                0.0, // costUsd는 Spring이 토큰 수로 직접 계산(요금 정책 변경에 즉시 대응 가능하도록)
                root.path("latencyMs").asLong(0)
        );
    }

    private AnalysisResponseDto parseAnalysisResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        boolean success = root.path("success").asBoolean(false);
        if (!success) {
            return AnalysisResponseDto.error(root.path("errorMessage").asText("LLM 서비스 오류"));
        }

        List<AnalysisItemDto> items = new ArrayList<>();
        for (JsonNode node : root.path("items")) {
            items.add(new AnalysisItemDto(
                    node.path("analysisCategory").asText(""),
                    node.path("targetKey").asText(""),
                    node.path("materialImpact").asText(""),
                    node.path("riskLevel").asText("Neutral"),
                    node.path("charOffsetStart").asInt(-1),
                    node.path("charOffsetEnd").asInt(-1)
            ));
        }

        return AnalysisResponseDto.ok(
                items,
                root.path("droppedCount").asInt(0),
                root.path("tokensIn").asInt(0),
                root.path("tokensOut").asInt(0),
                0.0,
                root.path("latencyMs").asLong(0)
        );
    }
}
