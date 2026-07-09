package com.kosta.darfin.service.analysis;

import com.kosta.darfin.dto.analysis.MonitoredCompanyListResponse;
import com.kosta.darfin.dto.analysis.MonitoredCompanyResponse;
import com.kosta.darfin.entity.analysis.MonitoredCompany;
import com.kosta.darfin.entity.common.Stock;
import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.repository.analysis.MonitoredCompanyRepository;
import com.kosta.darfin.repository.common.StockRepository;
import com.kosta.darfin.repository.common.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonitoredCompanyService {

    private static final DateTimeFormatter ADDED_AT_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final Map<String, Integer> MONITOR_LIMIT_BY_PLAN = Map.of(
            "FREE", 3,
            "BASIC", 3,
            "PRO", 10,
            "ENTERPRISE", 50
    );

    private static final int DEFAULT_MONITOR_LIMIT = 3;

    private final MonitoredCompanyRepository monitoredCompanyRepository;
    private final UsersRepository usersRepository;
    private final StockRepository stockRepository;
    private final CompanySearchService companySearchService;

    public MonitoredCompanyListResponse listMonitored(String email) {
        Users user = findUser(email);
        int limit = monitorLimit(user.getSubscriptionLevel());
        List<MonitoredCompanyResponse> items = monitoredCompanyRepository
                .findByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return MonitoredCompanyListResponse.builder()
                .items(items)
                .count(items.size())
                .limit(limit)
                .build();
    }

    @Transactional
    public MonitoredCompanyResponse addMonitored(String email, String corpCode) {
        Users user = findUser(email);
        Stock stock = stockRepository.findByDartCorpCode(corpCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "DART에 등록되지 않은 기업입니다."));
        if (stock.getStockCode() == null || stock.getStockCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상장 종목이 아닙니다.");
        }

        MonitoredCompany existing = monitoredCompanyRepository
                .findByUser_IdAndCorpCode(user.getId(), corpCode)
                .orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        int limit = monitorLimit(user.getSubscriptionLevel());
        if (monitoredCompanyRepository.countByUser_Id(user.getId()) >= limit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "모니터링 한도(" + limit + "개)에 도달했습니다.");
        }

        MonitoredCompany saved = monitoredCompanyRepository.save(MonitoredCompany.builder()
                .user(user)
                .corpCode(corpCode)
                .createdAt(LocalDateTime.now())
                .build());

        companySearchService.onboard(corpCode);

        return toResponse(saved);
    }

    @Transactional
    public void removeMonitored(String email, String corpCode) {
        Users user = findUser(email);
        monitoredCompanyRepository.deleteByUser_IdAndCorpCode(user.getId(), corpCode);
    }

    private MonitoredCompanyResponse toResponse(MonitoredCompany row) {
        Stock stock = stockRepository.findByDartCorpCode(row.getCorpCode()).orElse(null);
        return MonitoredCompanyResponse.builder()
                .corpCode(row.getCorpCode())
                .name(stock != null ? stock.getCompanyName() : row.getCorpCode())
                .ticker(stock != null ? stock.getStockCode() : "")
                .addedAt(row.getCreatedAt().format(ADDED_AT_FORMAT))
                .build();
    }

    private int monitorLimit(String subscriptionLevel) {
        if (subscriptionLevel == null || subscriptionLevel.isBlank()) {
            return DEFAULT_MONITOR_LIMIT;
        }
        return MONITOR_LIMIT_BY_PLAN.getOrDefault(subscriptionLevel.toUpperCase(), DEFAULT_MONITOR_LIMIT);
    }

    private Users findUser(String email) {
        return usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
