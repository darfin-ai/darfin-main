package com.kosta.darfin.service.fund;

import com.kosta.darfin.entity.fund.StockPriceRealtime;
import com.kosta.darfin.repository.fund.StockPriceRealtimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 실시간 시세 캐시 우선 조회.
 * 1) DB에 최근 30초 이내 시세가 있으면 → DB 값 반환 (KIS 호출 없음)
 * 2) 없거나 30초 초과면 → KIS 호출 후 새 행 INSERT
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private static final long CACHE_TTL_SECONDS = 30;

    private final StockPriceRealtimeRepository priceRepository;
    private final KisApiClient kisApiClient;

    public StockPriceRealtime getOrFetch(String stockCode) {
        Optional<StockPriceRealtime> cached = priceRepository.findLatestByStockCode(stockCode);

        if (cached.isPresent() && !isStale(cached.get())) {
            return cached.get();
        }

        return fetchFromKisAndSave(stockCode);
    }

    private boolean isStale(StockPriceRealtime price) {
        return price.getFetchedAt() == null
                || price.getFetchedAt().isBefore(LocalDateTime.now().minusSeconds(CACHE_TTL_SECONDS));
    }

    private StockPriceRealtime fetchFromKisAndSave(String stockCode) {
        log.info("실시간 시세 캐시 만료/없음 — KIS API 호출: stockCode={}", stockCode);

        KisApiClient.StockBasicInfo fetched = kisApiClient.fetchStockBasicInfo(stockCode);
        LocalDateTime now = LocalDateTime.now();

        // 전일 종가 = 현재가 - (현재가 * 등락률/100) 으로 역산
        long prevClose = (long) (fetched.getCurrentPrice() / (1 + fetched.getChangeRate() / 100));

        priceRepository.insertPrice(
                stockCode,
                fetched.getCurrentPrice(),
                prevClose,
                fetched.getChangeRate(),
                fetched.getVolume(),
                fetched.getHigh52w(),
                fetched.getLow52w(),
                now
        );

        return priceRepository.findLatestByStockCode(stockCode)
                .orElseThrow(() -> new IllegalStateException("저장 직후 조회 실패: " + stockCode));
    }
}