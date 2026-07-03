package com.kosta.darfin.service.disclosure;

import com.kosta.darfin.dto.disclosure.DartCollectRequestDto;
import com.kosta.darfin.dto.disclosure.DartCollectResponseDto;
import com.kosta.darfin.entity.common.Stock;
import com.kosta.darfin.entity.disclosure.Disclosure;
import com.kosta.darfin.entity.disclosure.DisclosureType;
import com.kosta.darfin.repository.common.StockRepository;
import com.kosta.darfin.repository.disclosure.DisclosureRepository;
import com.kosta.darfin.repository.disclosure.DisclosureTypeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;


@Service
public class DartCollectService {

    private static final Logger log = LoggerFactory.getLogger(DartCollectService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${llm.service.base-url:http://127.0.0.1:8001}")
    private String llmServiceBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StockRepository stockRepository;
    private final DisclosureRepository disclosureRepository;
    private final DisclosureTypeRepository disclosureTypeRepository;

    public DartCollectService(RestTemplate restTemplate,
                               ObjectMapper objectMapper,
                               StockRepository stockRepository,
                               DisclosureRepository disclosureRepository,
                               DisclosureTypeRepository disclosureTypeRepository) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.stockRepository = stockRepository;
        this.disclosureRepository = disclosureRepository;
        this.disclosureTypeRepository = disclosureTypeRepository;
    }

    @Transactional
    public DartCollectResponseDto collect(DartCollectRequestDto req) {

        
        JsonNode root;
        try {
            root = callPythonCollect(req);
        } catch (Exception e) {
            log.error("Python /dart/collect 호출 실패", e);
            return DartCollectResponseDto.error("Python 수집 서비스 호출 실패: " + e.getMessage());
        }

        boolean success = root.path("success").asBoolean(false);
        if (!success) {
            String msg = root.path("errorMessage").asText("알 수 없는 수집 오류");
            return DartCollectResponseDto.error(msg);
        }

        
        JsonNode corpNode = root.path("corp");
        int savedStockCount = 0;

        if (!corpNode.isMissingNode() && !corpNode.isNull()) {
            String dartCorpCode = corpNode.path("dartCorpCode").asText("").trim();
            String companyName  = corpNode.path("companyName").asText("").trim();
            String stockCode    = nullIfBlank(corpNode.path("stockCode").asText(""));
            String marketType   = corpNode.path("marketType").asText("비상장").trim();

            if (!dartCorpCode.isEmpty()) {
                Stock stock = stockRepository.findByDartCorpCode(dartCorpCode)
                        .orElse(new Stock());
                stock.setDartCorpCode(dartCorpCode);
                stock.setCompanyName(companyName);
                stock.setStockCode(stockCode);
                stock.setMarketType(marketType);
                stockRepository.save(stock);
                savedStockCount = 1;
                log.info("[stock UPSERT] {} ({})", companyName, dartCorpCode);
            }
        }

        
        JsonNode disclosures = root.path("disclosures");
        int saved = 0;
        int skipped = 0;

        for (JsonNode item : disclosures) {
            String rceptNo      = item.path("rceptNo").asText("").trim();
            String dartCorpCode = item.path("dartCorpCode").asText("").trim();
            String typeCode     = item.path("typeCode").asText("OTHER").trim();
            String title        = item.path("title").asText("").trim();
            String filerName    = item.path("filerName").asText("").trim();
            String filedAtStr   = item.path("filedAt").asText("").trim();

            if (rceptNo.isEmpty() || dartCorpCode.isEmpty()) {
                skipped++;
                continue;
            }

            
            Optional<DisclosureType> typeOpt = disclosureTypeRepository.findById(typeCode);
            if (typeOpt.isEmpty()) {
                log.debug("[skip] type_code='{}' 는 disclosure_type에 미등록 — rcept_no={}", typeCode, rceptNo);
                skipped++;
                continue;
            }

           
            Optional<Stock> stockOpt = stockRepository.findByDartCorpCode(dartCorpCode);
            if (stockOpt.isEmpty()) {
                log.warn("[skip] dart_corp_code='{}' 가 stock에 없음 — rcept_no={}", dartCorpCode, rceptNo);
                skipped++;
                continue;
            }

           
            if (disclosureRepository.existsById(rceptNo)) {
                log.debug("[이미존재] rcept_no={}", rceptNo);
                continue;
            }

            LocalDate filedAt = parseDate(filedAtStr);
            if (filedAt == null) {
                skipped++;
                continue;
            }

            Disclosure disclosure = new Disclosure();
            disclosure.setRceptNo(rceptNo);
            disclosure.setStock(stockOpt.get());
            disclosure.setDisclosureType(typeOpt.get());
            disclosure.setTitle(title);
            disclosure.setFilerName(filerName);
            disclosure.setFiledAt(filedAt);

            disclosureRepository.save(disclosure);
            saved++;
        }

        log.info("[수집 완료] stock={}, disclosure 저장={}, 건너뜀={}", savedStockCount, saved, skipped);

        String companyName  = root.path("corp").path("companyName").asText("");
        String dartCorpCode = root.path("corp").path("dartCorpCode").asText("");
        return DartCollectResponseDto.ok(companyName, dartCorpCode, savedStockCount, saved, skipped);
    }

    private JsonNode callPythonCollect(DartCollectRequestDto req) throws Exception {
        String url = llmServiceBaseUrl + "/dart/collect";

        Map<String, String> body = Map.of(
                "companyName", req.getCompanyName(),
                "bgnDe", req.getBgnDe(),
                "endDe", req.getEndDe()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        String responseBody = restTemplate.postForObject(url, entity, String.class);
        return objectMapper.readTree(responseBody);
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s, DATE_FMT);
        } catch (DateTimeParseException e) {
            log.warn("날짜 파싱 실패: '{}'", s);
            return null;
        }
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
