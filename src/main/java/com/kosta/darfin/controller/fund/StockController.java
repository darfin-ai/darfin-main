package com.kosta.darfin.controller.fund;

import com.kosta.darfin.dto.fund.DailyPriceResponse;
import com.kosta.darfin.dto.fund.ExecutionResponse;
import com.kosta.darfin.dto.fund.OrderBookResponse;
import com.kosta.darfin.dto.fund.StockInfoResponse;
import com.kosta.darfin.dto.fund.StockSummaryDTO;
import com.kosta.darfin.entity.fund.StockInfo;
import com.kosta.darfin.entity.fund.StockPriceRealtime;
import com.kosta.darfin.service.fund.KisApiClient;
import com.kosta.darfin.service.fund.KisRankApiClient;
import com.kosta.darfin.service.fund.StockInfoService;
import com.kosta.darfin.service.fund.StockPriceService;
import com.kosta.darfin.service.fund.WatchlistBroadcastScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * URL 규칙: 도메인 prefix(funds) + kebab-case 복수형
 */
@Slf4j
@RestController("fundStockController")
@RequestMapping("/funds/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockInfoService stockInfoService;
    private final StockPriceService stockPriceService;
    private final KisApiClient kisApiClient;
    private final KisRankApiClient kisRankApiClient;
    private final WatchlistBroadcastScheduler watchlistBroadcastScheduler;

    @GetMapping("/{stockCode}")
    public StockInfo getStockInfo(@PathVariable String stockCode) {
        return stockInfoService.getOrFetch(stockCode);
    }

    @GetMapping("/{stockCode}/price")
    public StockPriceRealtime getStockPrice(@PathVariable String stockCode) {
        return stockPriceService.getOrFetch(stockCode);
    }

    /**
     * 일봉 차트 데이터 — 실전 도메인 호출 2회로 최대 200개(~10개월) 반환.
     * 오래된 순(oldest first) 정렬.
     */
    @GetMapping("/{stockCode}/candles")
    public List<KisRankApiClient.CandleData> getCandles(@PathVariable String stockCode) {
        return kisRankApiClient.fetchDailyCandles(stockCode);
    }

    /** 분봉 조회 — CandleData.date 가 HHMMSS 형식 */
    @GetMapping("/{stockCode}/candles/intraday")
    public List<KisRankApiClient.CandleData> getIntradayCandles(@PathVariable String stockCode) {
        return kisRankApiClient.fetchIntradayCandles(stockCode);
    }

    /** 시봉 조회 (주봉용) — CandleData.date 가 YYYYMMDDHH 형식 */
    @GetMapping("/{stockCode}/candles/weekly")
    public List<KisRankApiClient.CandleData> getWeeklyCandles(@PathVariable String stockCode) {
        return kisRankApiClient.fetchWeeklyIntradayCandles(stockCode);
    }

    /** 일별 시세 — 일자, 종가, 등락률, 거래량 (최신 순, 최대 100일) */
    @GetMapping("/{stockCode}/daily")
    public List<DailyPriceResponse> getDailyPrices(@PathVariable String stockCode) {
        return kisRankApiClient.fetchDailyPriceList(stockCode);
    }

    /** 호가 조회 — 매도/매수 각 5호가 + 현재가 */
    @GetMapping("/{stockCode}/orderbook")
    public OrderBookResponse getOrderBook(@PathVariable String stockCode) {
        return kisApiClient.fetchOrderBook(stockCode);
    }

    /** 최근 체결 목록 조회 */
    @GetMapping("/{stockCode}/executions")
    public List<ExecutionResponse> getRecentExecutions(@PathVariable String stockCode) {
        return kisApiClient.fetchRecentExecutions(stockCode);
    }

    /** 종목 개요 정보 — 시가총액, 52주 최고/최저, PER, 업종 */
    @GetMapping("/{stockCode}/info")
    public StockInfoResponse getStockInfoDetail(@PathVariable String stockCode) {
        KisApiClient.StockBasicInfo raw;
        try {
            raw = kisApiClient.fetchStockBasicInfo(stockCode);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다.");
        }
        // KIS가 존재하지 않는 종목에 대해 현재가 0을 반환하는 경우 404 처리
        if (raw.getCurrentPrice() == null || raw.getCurrentPrice() == 0L) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다.");
        }
        // stock_info DB에 없으면 lazy 적재 (모의투자 FK 참조 등을 위해)
        try {
            stockInfoService.saveIfAbsent(stockCode, raw);
        } catch (Exception e) {
            log.warn("stock_info lazy 적재 실패 code={}: {}", stockCode, e.getMessage());
        }
        return StockInfoResponse.builder()
                .marketCap(raw.getMarketCap())
                .week52High(raw.getHigh52w())
                .week52Low(raw.getLow52w())
                .per(raw.getPer() != null && raw.getPer() != 0.0 ? raw.getPer() : null)
                .sector(raw.getSector() != null && !raw.getSector().isBlank() ? raw.getSector() : null)
                .build();
    }

    @GetMapping("/{stockCode}/summary")
    public StockSummaryDTO getStockSummary(@PathVariable String stockCode) {
        KisApiClient.StockBasicInfo raw = kisApiClient.fetchStockBasicInfo(stockCode);
        return toSummaryDto(stockCode, raw);
    }

    /**
     * 관심종목 시세 일괄 조회 — 캐시 우선, 없으면 순차 KIS 호출.
     * WebSocket SUBSCRIBE 사용 시 이 엔드포인트 호출 불필요.
     */
    @GetMapping("/summaries")
    public List<StockSummaryDTO> getStockSummaries(@RequestParam String codes) {
        List<String> codeList = Arrays.stream(codes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        watchlistBroadcastScheduler.registerCodes(codeList);

        List<StockSummaryDTO> cached = watchlistBroadcastScheduler.getCached(codeList);
        if (!cached.isEmpty()) return cached;

        // 캐시 미스 시 순차 조회 (rate limit은 KisApiClient 내부에서 처리)
        List<StockSummaryDTO> results = new ArrayList<>();
        for (String code : codeList) {
            try {
                KisApiClient.StockBasicInfo info = kisApiClient.fetchStockBasicInfo(code);
                results.add(toSummaryDto(code, info));
            } catch (Exception e) {
                log.warn("종목 {} 시세 조회 실패, 스킵: {}", code, e.getMessage());
            }
        }
        return results;
    }

    /**
     * StockSummaryDTO.pct 는 BigDecimal 타입.
     * KisApiClient.StockBasicInfo.changeRate 는 Double이라 변환 필요.
     */
    private StockSummaryDTO toSummaryDto(String stockCode, KisApiClient.StockBasicInfo raw) {
        // acml_tr_pbmn 단위 검증: 토스증권 실제 거래대금(SK하이닉스 7,365억)과 비교 시
        // 100_000_000(1억)으로 나누면 10배 부풀려진 값이 나옴 → 1_000_000_000(10억)으로 나눠야 정확함
        long valueInEok = raw.getTradeValue() != null ? raw.getTradeValue() / 1_000_000_000L : 0L;
        long currentPrice = raw.getCurrentPrice() != null ? raw.getCurrentPrice() : 0L;

        BigDecimal pct = raw.getChangeRate() != null
                ? BigDecimal.valueOf(raw.getChangeRate())
                : BigDecimal.ZERO;

        String stockName = stockInfoService.getCachedNameOrFallback(stockCode, raw.getStockName());

        String logoUrl = "https://file.alphasquare.co.kr/media/images/stock_logo/kr/" + stockCode + ".png";
        return new StockSummaryDTO(stockCode, stockName, currentPrice, pct, valueInEok, logoUrl);
    }

}