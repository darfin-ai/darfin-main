package com.kosta.darfin.controller.disclosure;

import com.kosta.darfin.dto.disclosure.DisclosureSearchCondition;
import com.kosta.darfin.dto.disclosure.DisclosureSearchResponse;
import com.kosta.darfin.dto.disclosure.TodayDisclosureDto;
import com.kosta.darfin.service.disclosure.DisclosureSearchService;
import com.kosta.darfin.service.disclosure.DisclosureTodayService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;


@RestController
public class DisclosureSearchController {

    private final DisclosureSearchService searchService;
    private final DisclosureTodayService todayService;

    public DisclosureSearchController(DisclosureSearchService searchService,
                                       DisclosureTodayService todayService) {
        this.searchService = searchService;
        this.todayService = todayService;
    }

    /** GET /api/disclosures/today — 검색 전 화면에 보여줄 "오늘 올라온 공시" 최신 N건 */
    @GetMapping("/api/disclosures/today")
    public ResponseEntity<List<TodayDisclosureDto>> today(
            @RequestParam(defaultValue = "6") int limit
    ) {
        return ResponseEntity.ok(todayService.getTodayDisclosures(limit));
    }

    @GetMapping("/api/disclosures")
    public ResponseEntity<DisclosureSearchResponse> search(
            @RequestParam(required = false) String companyName,
            // @DateTimeFormat이 없으면 "2026-01-01" 같은 ISO 날짜 문자열을 LocalDate로
            // 변환하지 못하고 MethodArgumentTypeMismatchException(HTTP 400)이 발생한다.
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) List<String> typeCodes,
            @RequestParam(required = false, defaultValue = "date") String sortKey,
            @RequestParam(required = false, defaultValue = "desc") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        DisclosureSearchCondition condition = new DisclosureSearchCondition();
        condition.setCompanyName(companyName);
        condition.setDateFrom(dateFrom);
        condition.setDateTo(dateTo);
        condition.setTypeCodes(typeCodes);
        condition.setSortKey(sortKey);
        condition.setSortDirection(sortDirection);

        // 정렬은 QueryDSL의 OrderSpecifier로 직접 처리하므로 Pageable에는 정렬을 담지 않는다
        // (Sort.unsorted() + offset/limit만 사용).
        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(searchService.searchWithAutoCollect(condition, pageable));
    }
}
