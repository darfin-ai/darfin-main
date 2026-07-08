package com.kosta.darfin.service.disclosure;

import com.kosta.darfin.dto.disclosure.TodayDisclosureDto;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "오늘 올라온 공시" 피드. DisclosureSearchService(기업명 지정 자동수집)와 달리
 * 회사 지정 없이 Python /dart/today-disclosures를 호출해 오늘자 전체 공시 중
 * 최신 N건을 가져오고, DartCollectService와 동일한 UPSERT 패턴으로 stock/disclosure에
 * 저장한다 — 그래야 피드 항목을 클릭했을 때 상세 페이지(DB 조회 전용)가 바로 열린다.
 */
@Service
public class DisclosureTodayService {

    private static final Logger log = LoggerFactory.getLogger(DisclosureTodayService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${llm.service.base-url:http://127.0.0.1:8002}")
    private String llmServiceBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StockRepository stockRepository;
    private final DisclosureRepository disclosureRepository;
    private final DisclosureTypeRepository disclosureTypeRepository;

    public DisclosureTodayService(RestTemplate restTemplate,
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
    public List<TodayDisclosureDto> getTodayDisclosures(int limit) {
        JsonNode root;
        try {
            root = callPythonToday(limit);
        } catch (Exception e) {
            log.error("Python /dart/today-disclosures 호출 실패", e);
            return List.of();
        }

        if (!root.path("success").asBoolean(false)) {
            log.warn("[오늘의 공시 조회 실패] {}", root.path("errorMessage").asText(""));
            return List.of();
        }

        List<TodayDisclosureDto> result = new ArrayList<>();

        for (JsonNode item : root.path("items")) {
            String rceptNo = item.path("rceptNo").asText("").trim();
            String dartCorpCode = item.path("dartCorpCode").asText("").trim();
            String typeCode = item.path("typeCode").asText("OTHER").trim();
            String title = item.path("title").asText("").trim();
            String filerName = item.path("filerName").asText("").trim();
            String companyName = item.path("companyName").asText("").trim();
            String stockCode = nullIfBlank(item.path("stockCode").asText(""));
            String marketType = item.path("marketType").asText("비상장").trim();
            String filedAtStr = item.path("filedAt").asText("").trim();

            if (rceptNo.isEmpty() || dartCorpCode.isEmpty()) {
                continue;
            }

            Optional<DisclosureType> typeOpt = disclosureTypeRepository.findById(typeCode);
            if (typeOpt.isEmpty()) {
                log.debug("[skip] type_code='{}' 미등록 — rcept_no={}", typeCode, rceptNo);
                continue;
            }
            DisclosureType type = typeOpt.get();

            Stock stock = stockRepository.findByDartCorpCode(dartCorpCode).orElse(null);
            if (stock == null) {
                stock = new Stock();
                stock.setDartCorpCode(dartCorpCode);
                stock.setCompanyName(companyName);
                stock.setStockCode(stockCode);
                stock.setMarketType(marketType);
                stock = stockRepository.save(stock);
            }

            Disclosure disclosure = disclosureRepository.findById(rceptNo).orElse(null);
            if (disclosure == null) {
                LocalDate filedAt = parseDate(filedAtStr);
                if (filedAt == null) {
                    continue;
                }
                disclosure = new Disclosure();
                disclosure.setRceptNo(rceptNo);
                disclosure.setStock(stock);
                disclosure.setDisclosureType(type);
                disclosure.setTitle(title);
                disclosure.setFilerName(filerName);
                disclosure.setFiledAt(filedAt);
                disclosure = disclosureRepository.save(disclosure);
            }

            result.add(new TodayDisclosureDto(
                    disclosure.getRceptNo(),
                    title,
                    disclosure.getFiledAt(),
                    type.getTypeCode(),
                    type.getTypeName(),
                    companyName,
                    filerName,
                    disclosure.getCreatedAt()
            ));

            if (result.size() >= limit) {
                break;
            }
        }

        return result;
    }

    private JsonNode callPythonToday(int limit) throws Exception {
        String url = UriComponentsBuilder.fromHttpUrl(llmServiceBaseUrl + "/dart/today-disclosures")
                .queryParam("limit", limit)
                .toUriString();

        String responseBody = restTemplate.getForObject(url, String.class);
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
