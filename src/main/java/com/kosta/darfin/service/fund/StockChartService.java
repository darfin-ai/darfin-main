package com.kosta.darfin.service.fund;

import com.kosta.darfin.dto.fund.StockTickDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 기업 상세 차트 캐시.
 * KIS REST 캔들은 짧게 캐싱하고, 응답 직전에 최근 WebSocket 체결 틱으로 마지막 봉만 보정한다.
 */
@Service
@RequiredArgsConstructor
public class StockChartService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KisRankApiClient kisRankApiClient;
    private final KisRealtimeClient kisRealtimeClient;

    @Value("${darfin.chart-cache.daily-ttl-seconds:900}")
    private long dailyTtlSeconds;

    @Value("${darfin.chart-cache.intraday-ttl-seconds:10}")
    private long intradayTtlSeconds;

    @Value("${darfin.chart-cache.weekly-ttl-seconds:60}")
    private long weeklyTtlSeconds;

    @Value("${darfin.chart-cache.tick-overlay-max-age-seconds:15}")
    private long tickOverlayMaxAgeSeconds;

    private final ConcurrentHashMap<String, CacheEntry> dailyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry> intradayCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry> weeklyCache = new ConcurrentHashMap<>();

    public List<KisRankApiClient.CandleData> getDailyCandles(String stockCode) {
        List<KisRankApiClient.CandleData> candles = getCached(
                dailyCache,
                stockCode,
                dailyTtlSeconds,
                () -> kisRankApiClient.fetchDailyCandles(stockCode)
        );
        return overlayDailyTick(stockCode, candles);
    }

    public List<KisRankApiClient.CandleData> getIntradayCandles(String stockCode) {
        List<KisRankApiClient.CandleData> candles = getCached(
                intradayCache,
                stockCode,
                intradayTtlSeconds,
                () -> kisRankApiClient.fetchIntradayCandles(stockCode)
        );
        return overlayIntradayTick(stockCode, candles);
    }

    public List<KisRankApiClient.CandleData> getWeeklyCandles(String stockCode) {
        List<KisRankApiClient.CandleData> candles = getCached(
                weeklyCache,
                stockCode,
                weeklyTtlSeconds,
                () -> kisRankApiClient.fetchWeeklyIntradayCandles(stockCode)
        );
        return overlayWeeklyTick(stockCode, candles);
    }

    private synchronized List<KisRankApiClient.CandleData> getCached(
            ConcurrentHashMap<String, CacheEntry> cache,
            String stockCode,
            long ttlSeconds,
            Supplier<List<KisRankApiClient.CandleData>> loader
    ) {
        CacheEntry cached = cache.get(stockCode);
        if (cached != null && !cached.isExpired(ttlSeconds)) {
            return copy(cached.candles);
        }

        List<KisRankApiClient.CandleData> loaded = loader.get();
        cache.put(stockCode, new CacheEntry(copy(loaded), LocalDateTime.now()));
        return copy(loaded);
    }

    private List<KisRankApiClient.CandleData> overlayDailyTick(
            String stockCode,
            List<KisRankApiClient.CandleData> candles
    ) {
        StockTickDTO tick = recentTick(stockCode);
        if (tick == null || tick.getPrice() == null) return candles;

        String today = LocalDate.now().format(DATE_FORMATTER);
        long price = tick.getPrice();
        long volume = tick.getVolume() != null ? tick.getVolume() : 0L;
        if (candles.isEmpty()) {
            candles.add(new KisRankApiClient.CandleData(today, price, price, price, price, volume));
            return candles;
        }

        int lastIndex = candles.size() - 1;
        KisRankApiClient.CandleData last = candles.get(lastIndex);
        if (today.equals(last.getDate())) {
            candles.set(lastIndex, withPrice(last, price, volume > 0 ? volume : last.getVolume()));
        } else if (today.compareTo(last.getDate()) > 0) {
            candles.add(new KisRankApiClient.CandleData(today, price, price, price, price, volume));
        }
        return candles;
    }

    private List<KisRankApiClient.CandleData> overlayIntradayTick(
            String stockCode,
            List<KisRankApiClient.CandleData> candles
    ) {
        StockTickDTO tick = recentTick(stockCode);
        String tickTime = tickTime(tick);
        if (tick == null || tick.getPrice() == null || tickTime == null) return candles;

        String tickMinute = tickTime.substring(0, 4);
        long price = tick.getPrice();
        int lastIndex = candles.size() - 1;
        if (lastIndex >= 0) {
            KisRankApiClient.CandleData last = candles.get(lastIndex);
            String lastMinute = compactTime(last.getDate());
            if (lastMinute != null && lastMinute.substring(0, 4).equals(tickMinute)) {
                candles.set(lastIndex, withPrice(last, price, last.getVolume()));
                return candles;
            }
            if (lastMinute != null && tickTime.compareTo(lastMinute) > 0) {
                candles.add(new KisRankApiClient.CandleData(tickMinute + "00", price, price, price, price, 0));
                return candles;
            }
        }

        if (candles.isEmpty()) {
            candles.add(new KisRankApiClient.CandleData(tickMinute + "00", price, price, price, price, 0));
        }
        return candles;
    }

    private List<KisRankApiClient.CandleData> overlayWeeklyTick(
            String stockCode,
            List<KisRankApiClient.CandleData> candles
    ) {
        StockTickDTO tick = recentTick(stockCode);
        String tickTime = tickTime(tick);
        if (tick == null || tick.getPrice() == null || tickTime == null) return candles;

        String currentHour = LocalDate.now().format(DATE_FORMATTER) + tickTime.substring(0, 2);
        long price = tick.getPrice();
        int lastIndex = candles.size() - 1;
        if (lastIndex >= 0) {
            KisRankApiClient.CandleData last = candles.get(lastIndex);
            if (currentHour.equals(last.getDate())) {
                candles.set(lastIndex, withPrice(last, price, last.getVolume()));
                return candles;
            }
            if (currentHour.compareTo(last.getDate()) > 0) {
                candles.add(new KisRankApiClient.CandleData(currentHour, price, price, price, price, 0));
                return candles;
            }
        }

        if (candles.isEmpty()) {
            candles.add(new KisRankApiClient.CandleData(currentHour, price, price, price, price, 0));
        }
        return candles;
    }

    private StockTickDTO recentTick(String stockCode) {
        StockTickDTO tick = kisRealtimeClient.getLastTick(stockCode);
        LocalDateTime receivedAt = kisRealtimeClient.getLastTickReceivedAt(stockCode);
        if (tick == null || receivedAt == null) return null;

        long ageSeconds = Duration.between(receivedAt, LocalDateTime.now()).getSeconds();
        return ageSeconds <= tickOverlayMaxAgeSeconds ? tick : null;
    }

    private String tickTime(StockTickDTO tick) {
        if (tick == null || tick.getTime() == null) return null;
        String compact = tick.getTime().replace(":", "");
        return compact.length() == 6 ? compact : null;
    }

    private String compactTime(String candleDate) {
        if (candleDate == null) return null;
        if (candleDate.length() == 6) return candleDate;
        if (candleDate.length() == 14) return candleDate.substring(8);
        return null;
    }

    private KisRankApiClient.CandleData withPrice(KisRankApiClient.CandleData candle, long price, long volume) {
        return new KisRankApiClient.CandleData(
                candle.getDate(),
                candle.getOpen(),
                Math.max(candle.getHigh(), price),
                Math.min(candle.getLow(), price),
                price,
                volume
        );
    }

    private List<KisRankApiClient.CandleData> copy(List<KisRankApiClient.CandleData> source) {
        return new ArrayList<>(source);
    }

    private static class CacheEntry {
        private final List<KisRankApiClient.CandleData> candles;
        private final LocalDateTime cachedAt;

        private CacheEntry(List<KisRankApiClient.CandleData> candles, LocalDateTime cachedAt) {
            this.candles = candles;
            this.cachedAt = cachedAt;
        }

        private boolean isExpired(long ttlSeconds) {
            return cachedAt.plusSeconds(ttlSeconds).isBefore(LocalDateTime.now());
        }
    }
}
