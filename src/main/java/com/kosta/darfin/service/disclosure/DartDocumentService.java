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

import java.util.ArrayList;
import java.util.List;


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
            return DartDocumentResponseDto.ok(root.path("text").asText(""), parseBlocks(root.path("blocks")));
        } catch (Exception e) {
            log.error("공시 원문 조회 실패 rceptNo={}", rceptNo, e);
            return DartDocumentResponseDto.error("공시 원문 조회 실패: " + e.getMessage());
        }
    }

    /** Python이 내려주는 blocks(JSON 배열)를 DartDocumentResponseDto.Block 목록으로 변환한다. */
    private List<DartDocumentResponseDto.Block> parseBlocks(JsonNode blocksNode) {
        List<DartDocumentResponseDto.Block> blocks = new ArrayList<>();
        if (!blocksNode.isArray()) {
            return blocks;
        }

        for (JsonNode node : blocksNode) {
            DartDocumentResponseDto.Block block = new DartDocumentResponseDto.Block();
            block.setType(node.path("type").asText(null));
            block.setText(node.path("text").isMissingNode() ? null : node.path("text").asText(null));

            JsonNode rowsNode = node.path("rows");
            if (rowsNode.isArray()) {
                List<List<String>> rows = new ArrayList<>();
                for (JsonNode rowNode : rowsNode) {
                    List<String> row = new ArrayList<>();
                    if (rowNode.isArray()) {
                        for (JsonNode cellNode : rowNode) {
                            row.add(cellNode.asText(""));
                        }
                    }
                    rows.add(row);
                }
                block.setRows(rows);
            }

            JsonNode charStartNode = node.path("charStart");
            JsonNode charEndNode = node.path("charEnd");
            block.setCharStart(charStartNode.isNull() || charStartNode.isMissingNode() ? null : charStartNode.asInt());
            block.setCharEnd(charEndNode.isNull() || charEndNode.isMissingNode() ? null : charEndNode.asInt());

            blocks.add(block);
        }
        return blocks;
    }
}
