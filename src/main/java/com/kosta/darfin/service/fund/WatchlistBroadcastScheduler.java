package com.kosta.darfin.service.fund;

import com.kosta.darfin.dto.fund.StockSummaryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 관심종목 초기 스냅샷 캐시 — 실시간 갱신 자체는 KisRealtimeClient의 실제 KIS 틱(/topic/price/{code})이
 * 담당한다. 이 클래스는 /funds/stocks/summaries REST 호출(페이지 진입·관심종목 변경 시 1회)에 대한
 * 캐시 우선 응답만 제공한다 — 캐시 미스 시 호출부(StockController)가 KIS REST로 직접 채워 넣는다.
 */
@Slf4j
@Service
public class WatchlistBroadcastScheduler {

    // 마지막으로 조회 성공한 가격 캐시 (REST 재호출 시 즉시 응답용)
    private final ConcurrentHashMap<String, StockSummaryDTO> priceCache = new ConcurrentHashMap<>();

    public List<StockSummaryDTO> getCached(List<String> codes) {
        return codes.stream()
                .map(priceCache::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void cache(String code, StockSummaryDTO dto) {
        priceCache.put(code, dto);
    }
}