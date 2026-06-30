package com.kosta.darfin.service.fund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.darfin.dto.fund.StockSummaryDTO;
import com.kosta.darfin.websocket.StockWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class StockRankBroadcastScheduler {

    private final StockRankService stockRankService;
    private final StockWebSocketHandler stockWebSocketHandler;
    private final KisRealtimeClient kisRealtimeClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 마지막으로 브로드캐스트한 payload 캐시 (신규 접속자에게 즉시 전송용)
    private volatile String lastPayload;

    // 마지막 랭크 코드 목록 (구독 동기화 전용 — broadcast와 분리)
    private volatile Set<String> lastRankCodes = new java.util.HashSet<>();

    public StockRankBroadcastScheduler(StockRankService stockRankService,
                                       StockWebSocketHandler stockWebSocketHandler,
                                       KisRealtimeClient kisRealtimeClient) {
        this.stockRankService = stockRankService;
        this.stockWebSocketHandler = stockWebSocketHandler;
        this.kisRealtimeClient = kisRealtimeClient;
    }

    @PostConstruct
    public void init() {
        // 클라이언트 연결 시 캐시된 최신 랭크 데이터를 즉시 전송
        stockWebSocketHandler.setOnConnectCallback(this::sendCurrentRankTo);
    }

    /**
     * 10초마다 랭크 데이터를 갱신해서 연결된 모든 클라이언트에 브로드캐스트.
     * 연결된 클라이언트가 없으면 KIS API 호출 자체를 생략한다.
     * syncSubscriptions 는 별도 스케줄에서 실행 — 여기서는 REST 호출만.
     */
    @Scheduled(fixedDelay = 10000)
    public void broadcastRankData() {
        if (!stockWebSocketHandler.hasActiveSessions()) {
            return;
        }

        try {
            String payload = buildRankPayload();
            lastPayload = payload;
            stockWebSocketHandler.broadcast(payload);
            log.info("랭크 브로드캐스트 완료");
        } catch (Exception e) {
            log.error("랭크 브로드캐스트 실패", e);
        }
    }

    /**
     * 30초마다 KIS WebSocket 구독 목록 동기화.
     * broadcast와 분리해서 REST 호출과 WebSocket 구독이 겹치지 않게 한다.
     */
    @Scheduled(fixedDelay = 30000)
    public void syncKisSubscriptions() {
        if (lastRankCodes.isEmpty()) return;
        kisRealtimeClient.syncSubscriptions(lastRankCodes);
    }

    private void sendCurrentRankTo(WebSocketSession session) {
        // 캐시가 있을 때만 즉시 전송.
        // 캐시가 없으면(앱 시작 직후) broadcastRankData()와 동시에 KIS API를 호출해 EGW00201 발생하므로
        // 여기서는 API를 직접 호출하지 않고 스케줄러 브로드캐스트(최대 10초)를 기다린다.
        if (lastPayload != null) {
            stockWebSocketHandler.sendTo(session, lastPayload);
        }
    }

    private String buildRankPayload() throws Exception {
        // API 4번 → 2번으로 감소 (fetchVolumeRank("0") 중복 3회 제거)
        StockRankService.AllRankResult ranks = stockRankService.getAllRanks();

        Set<String> codes = Stream.of(ranks.getTradeValue(), ranks.getVolume(),
                        ranks.getTopGainers(), ranks.getTopLosers())
                .flatMap(List::stream)
                .map(StockSummaryDTO::getCode)
                .collect(Collectors.toSet());
        lastRankCodes = codes;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "RANK");
        payload.put("tradeValue", ranks.getTradeValue());
        payload.put("volume", ranks.getVolume());
        payload.put("topGainers", ranks.getTopGainers());
        payload.put("topLosers", ranks.getTopLosers());
        payload.put("timestamp", System.currentTimeMillis());

        return objectMapper.writeValueAsString(payload);
    }
}