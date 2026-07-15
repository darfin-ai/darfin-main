package com.kosta.darfin.service.analysis;

import com.kosta.darfin.dto.analysis.StarredCompanyListResponse;
import com.kosta.darfin.dto.analysis.StarredCompanyResponse;
import com.kosta.darfin.entity.analysis.StarredCompany;
import com.kosta.darfin.entity.common.Stock;
import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.repository.analysis.StarredCompanyRepository;
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
import java.util.stream.Collectors;

/**
 * 관심 기업(watchlist) — 무료·무제한 북마크. AI 분석 열람권과는 독립이라
 * 별표를 해제해도 열람권(user_content_unlocks)은 유지된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StarredCompanyService {

    private static final DateTimeFormatter ADDED_AT_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final StarredCompanyRepository starredCompanyRepository;
    private final UsersRepository usersRepository;
    private final StockRepository stockRepository;
    private final CompanySearchService companySearchService;
    private final OnboardIngestQueue onboardIngestQueue;

    public StarredCompanyListResponse listStarred(String email) {
        Users user = findUser(email);
        List<StarredCompanyResponse> items = starredCompanyRepository
                .findByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return StarredCompanyListResponse.builder()
                .items(items)
                .count(items.size())
                .build();
    }

    @Transactional
    public StarredCompanyResponse addStarred(String email, String corpCode) {
        Users user = findUser(email);
        Stock stock = stockRepository.findByDartCorpCode(corpCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "DART에 등록되지 않은 기업입니다."));
        if (stock.getStockCode() == null || stock.getStockCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상장 종목이 아닙니다.");
        }

        StarredCompany existing = starredCompanyRepository
                .findByUser_IdAndCorpCode(user.getId(), corpCode)
                .orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        StarredCompany saved = starredCompanyRepository.save(StarredCompany.builder()
                .user(user)
                .corpCode(corpCode)
                .createdAt(LocalDateTime.now())
                .build());

        companySearchService.onboard(corpCode);
        onboardIngestQueue.enqueueIfNeeded(corpCode);

        return toResponse(saved);
    }

    @Transactional
    public void removeStarred(String email, String corpCode) {
        Users user = findUser(email);
        starredCompanyRepository.deleteByUser_IdAndCorpCode(user.getId(), corpCode);
    }

    private StarredCompanyResponse toResponse(StarredCompany row) {
        Stock stock = stockRepository.findByDartCorpCode(row.getCorpCode()).orElse(null);
        return StarredCompanyResponse.builder()
                .corpCode(row.getCorpCode())
                .name(stock != null ? stock.getCompanyName() : row.getCorpCode())
                .ticker(stock != null ? stock.getStockCode() : "")
                .addedAt(row.getCreatedAt().format(ADDED_AT_FORMAT))
                .build();
    }

    private Users findUser(String email) {
        return usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
