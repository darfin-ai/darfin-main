package com.kosta.darfin.service.disclosure;

import com.kosta.darfin.dto.disclosure.DartDocumentResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;


@Service
public class DartDocumentService {

    private static final Logger log = LoggerFactory.getLogger(DartDocumentService.class);

    @Value("${llm.service.base-url:http://127.0.0.1:8001}")
    private String llmServiceBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DartDocumentService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public DartDocumentResponseDto fetchOriginalText(String rceptNo) {
        String url = UriComponentsBuilder.fromHttpUrl(llmServiceBaseUrl + "/dart/document/{rceptNo}")
                .buildAndExpand(rceptNo)
                .toUriString();

        try {
            String responseBody = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(responseBody);

            boolean success = root.path("success").asBoolean(false);
            if (!success) {
                return DartDocumentResponseDto.error(root.path("errorMessage").asText("공시 원문 조회 실패"));
            }
            return DartDocumentResponseDto.ok(root.path("text").asText(""));
        } catch (Exception e) {
            log.error("공시 원문 조회 실패 rceptNo={}", rceptNo, e);
            return DartDocumentResponseDto.error("공시 원문 조회 실패: " + e.getMessage());
        }
    }
}
