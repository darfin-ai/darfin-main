package com.kosta.darfin.controller.auth;

import com.kosta.darfin.dto.community.StockSearchResponse;
import com.kosta.darfin.service.community.DartApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/community/stocks")
@RequiredArgsConstructor
public class StockController {

    private final DartApiService dartApiService;

    /**
     * 종목 검색 (stock 테이블 DB 검색)
     * GET /api/community/stocks?keyword=삼성
     */
    @GetMapping
    public ResponseEntity<List<StockSearchResponse>> searchStocks(
            @RequestParam(required = false) String keyword) {
        List<StockSearchResponse> result = dartApiService.searchByKeyword(keyword).stream()
                .map(StockSearchResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * DART API에서 기업 목록을 동기화 (최초 1회 또는 갱신 시 사용)
     * POST /api/community/stocks/sync
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncStocks() {
        int count = dartApiService.syncCorpList();
        return ResponseEntity.ok(Map.of(
                "message", "DART 종목 동기화 완료",
                "count", count
        ));
    }
}
