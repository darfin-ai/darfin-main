package com.kosta.darfin.service.fund;

import com.kosta.darfin.entity.fund.StockInfo;
import com.kosta.darfin.repository.fund.StockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * stock_info 캐시 우선 조회.
 * 1) DB에 있고 1일 이내 갱신본이면 → DB 값 반환 (KIS 호출 없음)
 * 2) 없거나 오래됐으면 → KIS 호출 + stock 테이블 조회(종목명) 후 INSERT/UPDATE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockInfoService {

    private static final long CACHE_TTL_DAYS = 1;

    private final StockInfoRepository stockInfoRepository;
    private final KisApiClient kisApiClient;

    public StockInfo getOrFetch(String stockCode) {
        Optional<StockInfo> cached = stockInfoRepository.findById(stockCode);

        if (cached.isPresent() && !isStale(cached.get())) {
            return cached.get();
        }

        return fetchFromKisAndSave(stockCode, cached.isPresent());
    }

    /**
     * KIS를 호출하지 않고, DB(stock 테이블)에서만 종목명을 가볍게 조회.
     * 없으면 호출자가 넘긴 fallback(보통 KIS 응답의 빈 문자열)을 그대로 사용.
     * /summaries 처럼 이미 KIS 호출을 끝낸 뒤 종목명만 보완할 때 사용 — 추가 KIS 호출 없음.
     */
    public String getCachedNameOrFallback(String stockCode, String fallback) {
        return stockInfoRepository.findCompanyNameByStockCode(stockCode)
                .filter(name -> name != null && !name.isBlank())
                .orElse(fallback);
    }

    /**
     * 관심종목/모의투자 FK 생성을 위해 stock 마스터 테이블의 기업명만으로 stock_info를 보장한다.
     * 가격·재무 필드는 이후 상세/요약 조회에서 KIS 응답으로 보강된다.
     */
    @Transactional
    public StockInfo getOrCreateFromStockMaster(String stockCode) {
        Optional<StockInfo> cached = stockInfoRepository.findById(stockCode);
        if (cached.isPresent()) return cached.get();

        String stockName = stockInfoRepository.findCompanyNameByStockCode(stockCode)
                .filter(name -> name != null && !name.isBlank())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다: " + stockCode));

        stockInfoRepository.insertStockInfo(
                stockCode, stockName, null, null,
                null, null, null, LocalDateTime.now()
        );
        log.info("stock_info 마스터 기반 적재: {} ({})", stockCode, stockName);

        return stockInfoRepository.findById(stockCode)
                .orElseThrow(() -> new IllegalStateException("저장 직후 조회 실패: " + stockCode));
    }

    private boolean isStale(StockInfo info) {
        return info.getUpdatedAt() == null
                || info.getUpdatedAt().isBefore(LocalDateTime.now().minusDays(CACHE_TTL_DAYS));
    }

    /**
     * 이미 가진 StockBasicInfo로 KIS 재호출 없이 stock_info를 적재.
     * /info 엔드포인트처럼 fetchStockBasicInfo를 먼저 호출한 뒤 결과를 재사용할 때 사용.
     */
    @Transactional
    public void saveIfAbsent(String stockCode, KisApiClient.StockBasicInfo fetched) {
        if (stockInfoRepository.existsById(stockCode)) return;
        String stockName = getCachedNameOrFallback(stockCode, fetched.getStockName());
        stockInfoRepository.insertStockInfo(
                stockCode, stockName, fetched.getMarket(), fetched.getSector(),
                fetched.getMarketCap(), fetched.getPer(), fetched.getPbr(), LocalDateTime.now()
        );
        log.info("stock_info lazy 적재: {} ({})", stockCode, stockName);
    }

    private StockInfo fetchFromKisAndSave(String stockCode, boolean alreadyExists) {
        log.info("stock_info 캐시 없음/만료 — KIS API 호출: stockCode={}", stockCode);

        KisApiClient.StockBasicInfo fetched = kisApiClient.fetchStockBasicInfo(stockCode);
        LocalDateTime now = LocalDateTime.now();

        String stockName = getCachedNameOrFallback(stockCode, fetched.getStockName());

        if (alreadyExists) {
            stockInfoRepository.updateStockInfo(
                    stockCode, stockName, fetched.getMarket(), fetched.getSector(),
                    fetched.getMarketCap(), fetched.getPer(), fetched.getPbr(), now
            );
        } else {
            stockInfoRepository.insertStockInfo(
                    stockCode, stockName, fetched.getMarket(), fetched.getSector(),
                    fetched.getMarketCap(), fetched.getPer(), fetched.getPbr(), now
            );
        }

        log.info("stock_info 캐싱 완료: {} ({})", stockCode, stockName);

        return stockInfoRepository.findById(stockCode)
                .orElseThrow(() -> new IllegalStateException("저장 직후 조회 실패: " + stockCode));
    }
}
