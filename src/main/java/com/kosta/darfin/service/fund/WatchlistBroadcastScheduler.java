package com.kosta.darfin.service.fund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.darfin.dto.fund.StockSummaryDTO;
import com.kosta.darfin.websocket.StockWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WatchlistBroadcastScheduler {

    private final KisApiClient kisApiClient;
    private final StockInfoService stockInfoService;
    private final StockWebSocketHandler wsHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 클라이언트가 구독 요청한 전체 종목 코드 (세션 무관)
    private final Set<String> watchedCodes = ConcurrentHashMap.newKeySet();

    // 마지막으로 조회 성공한 가격 캐시 (신규 접속자 즉시 응답 + stale fallback용)
    private final ConcurrentHashMap<String, StockSummaryDTO> priceCache = new ConcurrentHashMap<>();

    public WatchlistBroadcastScheduler(KisApiClient kisApiClient,
                                       StockInfoService stockInfoService,
                                       StockWebSocketHandler wsHandler) {
        this.kisApiClient = kisApiClient;
        this.stockInfoService = stockInfoService;
        this.wsHandler = wsHandler;
    }

    @PostConstruct
    public void init() {
        wsHandler.setOnSubscribeCallback(this::handleSubscribe);
    }

    /**
     * 클라이언트가 WebSocket으로 {"type":"SUBSCRIBE","codes":[...]} 보낼 때 호출.
     * 코드를 watchedCodes에 등록하고, 캐시가 있으면 즉시 응답한다.
     */
    private void handleSubscribe(WebSocketSession session, List<String> codes) {
        watchedCodes.addAll(codes);
        sendCacheTo(session, codes);
    }

    private void sendCacheTo(WebSocketSession session, List<String> codes) {
        List<StockSummaryDTO> stocks = codes.stream()
                .map(priceCache::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (stocks.isEmpty()) return;

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "WATCHLIST");
            payload.put("stocks", stocks);
            wsHandler.sendTo(session, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("관심종목 초기 전송 실패", e);
        }
    }

    /**
     * 10초마다 등록된 관심종목 가격을 KIS REST로 갱신하고 브로드캐스트.
     * KisApiClient 내 rate limiter(250ms)가 호출 간격을 보장한다.
     */
    @Scheduled(fixedDelay = 10000)
    public void refreshAndBroadcast() {
        if (watchedCodes.isEmpty() || !wsHandler.hasActiveSessions()) return;

        List<StockSummaryDTO> stocks = new ArrayList<>();
        for (String code : new ArrayList<>(watchedCodes)) {
            try {
                KisApiClient.StockBasicInfo raw = kisApiClient.fetchStockBasicInfo(code);
                StockSummaryDTO dto = toDto(code, raw);
                priceCache.put(code, dto);
                stocks.add(dto);
            } catch (Exception e) {
                log.warn("관심종목 {} 조회 실패, stale 캐시 사용: {}", code, e.getMessage());
                StockSummaryDTO stale = priceCache.get(code);
                if (stale != null) stocks.add(stale);
            }
        }

        if (stocks.isEmpty()) return;

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "WATCHLIST");
            payload.put("stocks", stocks);
            payload.put("timestamp", System.currentTimeMillis());
            wsHandler.broadcast(objectMapper.writeValueAsString(payload));
            log.info("관심종목 브로드캐스트: {}개", stocks.size());
        } catch (Exception e) {
            log.error("관심종목 브로드캐스트 실패", e);
        }
    }

    /** REST 엔드포인트 fallback용 — 코드 등록 + 캐시 반환 */
    public void registerCodes(Collection<String> codes) {
        watchedCodes.addAll(codes);
    }

    public List<StockSummaryDTO> getCached(List<String> codes) {
        return codes.stream()
                .map(priceCache::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private StockSummaryDTO toDto(String code, KisApiClient.StockBasicInfo raw) {
        long valueInEok = raw.getTradeValue() != null ? raw.getTradeValue() / 1_000_000_000L : 0L;
        BigDecimal pct = raw.getChangeRate() != null
                ? BigDecimal.valueOf(raw.getChangeRate())
                : BigDecimal.ZERO;
        String name = stockInfoService.getCachedNameOrFallback(code, raw.getStockName());
        String logoUrl = "https://file.alphasquare.co.kr/media/images/stock_logo/kr/" + code + ".png";
        return new StockSummaryDTO(code, name,
                raw.getCurrentPrice() != null ? raw.getCurrentPrice() : 0L, pct, valueInEok, logoUrl);
    }
}