package com.kosta.darfin.controller.disclosure;

import com.kosta.darfin.dto.disclosure.DartDocumentResponseDto;
import com.kosta.darfin.dto.disclosure.DisclosureDetailResponse;
import com.kosta.darfin.dto.disclosure.TermHighlightDto;
import com.kosta.darfin.repository.disclosure.DisclosureRepository;
import com.kosta.darfin.service.disclosure.DartDocumentService;
import com.kosta.darfin.service.disclosure.DisclosureDetailService;
import com.kosta.darfin.service.disclosure.LlmPipelineClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.List;


@RestController
public class DisclosureDetailController {

    private static final Logger log = LoggerFactory.getLogger(DisclosureDetailController.class);

    @Value("${llm.service.base-url:http://127.0.0.1:8002}")
    private String llmServiceBaseUrl;

    @Value("${dart.api.key:}")
    private String dartApiKey;

    private final DisclosureDetailService detailService;
    private final DisclosureRepository disclosureRepository;
    private final DartDocumentService dartDocumentService;
    private final RestTemplate restTemplate;
    private final LlmPipelineClient llmPipelineClient;

    public DisclosureDetailController(DisclosureDetailService detailService,
                                       DisclosureRepository disclosureRepository,
                                       DartDocumentService dartDocumentService,
                                       RestTemplate restTemplate,
                                       LlmPipelineClient llmPipelineClient) {
        this.detailService = detailService;
        this.disclosureRepository = disclosureRepository;
        this.dartDocumentService = dartDocumentService;
        this.restTemplate = restTemplate;
        this.llmPipelineClient = llmPipelineClient;
    }

    /** GET /api/disclosures/{rceptNo} — 원문 메타 + 요약 + 분석 항목을 한 번에 반환 */
    @GetMapping("/api/disclosures/{rceptNo}")
    public ResponseEntity<?> getDetail(@PathVariable String rceptNo) {
        try {
            DisclosureDetailResponse response = detailService.getDetail(rceptNo);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /api/disclosures/{rceptNo}/original-text — DART document.xml에서 추출한 공시 원문 평문.
     * DisclosureViewer.jsx 좌측 "공시 원문" 탭에 표시되고, AI 요약/핵심 분석 버튼을 누를 때
     * 압축 입력(dartContext/dartFullText)으로 프론트가 그대로 재사용한다.
     */
    @GetMapping("/api/disclosures/{rceptNo}/original-text")
    public ResponseEntity<DartDocumentResponseDto> getOriginalText(@PathVariable String rceptNo) {
        DartDocumentResponseDto result = dartDocumentService.fetchOriginalText(rceptNo);
        if (!result.isSuccess()) {
            return ResponseEntity.internalServerError().body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/disclosures/{rceptNo}/terms
     * 공시 원문을 Python 용어사전 서비스(/glossary/terms)로 보내 등록된 전문용어를
     * 위치(startIndex/endIndex)와 함께 반환받는다. 용어 마스터 데이터와 매칭 로직은
     * 전부 Python 쪽(app/data/dictionary_terms.json)에서 관리한다.
     * 프론트는 이 좌표로 원문에 abbr 밑줄을 입히고, 용어사전 탭에 목록을 표시한다.
     */
    @GetMapping("/api/disclosures/{rceptNo}/terms")
    public ResponseEntity<List<TermHighlightDto>> getTermHighlights(@PathVariable String rceptNo) {
        DartDocumentResponseDto textResult = dartDocumentService.fetchOriginalText(rceptNo);
        if (!textResult.isSuccess() || textResult.getText() == null) {
            return ResponseEntity.ok(List.of());
        }
        List<TermHighlightDto> highlights = llmPipelineClient.requestTermHighlights(textResult.getText());
        return ResponseEntity.ok(highlights);
    }


     /* DART Open API에서 공시 원문 ZIP을 직접 받아 브라우저로 스트리밍한다.
     * Python을 거치지 않고 Spring이 DART API를 직접 호출한다 —
     * Python의 StreamingResponse 프록시 단계에서 Content-Type 불일치로
     * byte[] 변환이 실패하는 문제를 피하기 위해서다.
     */
    @GetMapping("/api/disclosures/{rceptNo}/download-zip")
    public ResponseEntity<byte[]> downloadZip(@PathVariable String rceptNo) {
        String dartUrl = "https://opendart.fss.or.kr/api/document.xml"
                + "?crtfc_key=" + dartApiKey
                + "&rcept_no=" + rceptNo;
        try {
            org.springframework.http.HttpHeaders reqHeaders = new org.springframework.http.HttpHeaders();
            reqHeaders.setAccept(java.util.List.of(org.springframework.http.MediaType.ALL));
            org.springframework.http.HttpEntity<Void> reqEntity =
                    new org.springframework.http.HttpEntity<>(reqHeaders);

            org.springframework.http.ResponseEntity<byte[]> dartResp = restTemplate.exchange(
                    dartUrl,
                    org.springframework.http.HttpMethod.GET,
                    reqEntity,
                    byte[].class
            );

            byte[] zipBytes = dartResp.getBody();
            if (zipBytes == null || zipBytes.length == 0) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + rceptNo + ".zip\"")
                    .body(zipBytes);
        } catch (Exception e) {
            log.error("DART 원문 ZIP 다운로드 실패 rceptNo={}", rceptNo, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/disclosures/{rceptNo}/download — disclosure.raw_zip_path가 가리키는 파일을 스트리밍.
     * raw_zip_path는 로컬 파일 경로 또는 오브젝트스토리지 키일 수 있는데, 지금은 로컬 파일시스템
     * 기준으로만 구현했다. S3/GCS 등을 쓴다면 이 메서드 내부만 해당 SDK 호출로 교체하면 된다.
     */
    @GetMapping("/api/disclosures/{rceptNo}/download")
    public ResponseEntity<Resource> download(@PathVariable String rceptNo) {
        return disclosureRepository.findById(rceptNo)
                .map(disclosure -> {
                    String path = disclosure.getRawZipPath();
                    if (path == null || path.isBlank()) {
                        return ResponseEntity.notFound().<Resource>build();
                    }

                    File file = new File(path);
                    if (!file.exists()) {
                        return ResponseEntity.notFound().<Resource>build();
                    }

                    Resource resource = new FileSystemResource(file);
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rceptNo + ".zip\"")
                            .body(resource);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
