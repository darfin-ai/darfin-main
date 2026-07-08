package com.kosta.darfin.service.analysis;

import com.kosta.darfin.dto.analysis.CompanyOnboardResponse;
import com.kosta.darfin.dto.analysis.CompanySearchResultResponse;
import com.kosta.darfin.entity.common.Stock;
import com.kosta.darfin.repository.common.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanySearchService {

    private static final int SEARCH_LIMIT = 20;

    private final StockRepository stockRepository;
    private final JdbcTemplate jdbcTemplate;

    public List<CompanySearchResultResponse> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        String trimmed = keyword.trim();
        List<Stock> stocks = stockRepository.findListedByKeyword(trimmed, PageRequest.of(0, SEARCH_LIMIT));
        if (stocks.isEmpty()) {
            return List.of();
        }

        List<String> corpCodes = stocks.stream().map(Stock::getDartCorpCode).collect(Collectors.toList());
        Set<String> analyzed = findAnalyzedCorpCodes(corpCodes);

        return stocks.stream()
                .map(stock -> CompanySearchResultResponse.builder()
                        .corpCode(stock.getDartCorpCode())
                        .name(stock.getCompanyName())
                        .ticker(stock.getStockCode())
                        .market(stock.getMarketType())
                        .analyzed(analyzed.contains(stock.getDartCorpCode()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public CompanyOnboardResponse onboard(String corpCode) {
        Stock stock = stockRepository.findByDartCorpCode(corpCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "DART에 등록되지 않은 기업입니다."));

        if (stock.getStockCode() == null || stock.getStockCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상장 종목이 아닙니다.");
        }

        boolean exists = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM companies WHERE corp_code = ?",
                Boolean.class,
                corpCode
        ));

        if (!exists) {
            jdbcTemplate.update("INSERT INTO companies (corp_code) VALUES (?)", corpCode);
        }

        return CompanyOnboardResponse.builder()
                .corpCode(corpCode)
                .name(stock.getCompanyName())
                .ticker(stock.getStockCode())
                .newlyCreated(!exists)
                .build();
    }

    private Set<String> findAnalyzedCorpCodes(List<String> corpCodes) {
        if (corpCodes.isEmpty()) {
            return Set.of();
        }
        String placeholders = corpCodes.stream().map(c -> "?").collect(Collectors.joining(", "));
        List<String> found = jdbcTemplate.queryForList(
                "SELECT corp_code FROM companies WHERE corp_code IN (" + placeholders + ")",
                String.class,
                corpCodes.toArray()
        );
        return new HashSet<>(found);
    }
}
