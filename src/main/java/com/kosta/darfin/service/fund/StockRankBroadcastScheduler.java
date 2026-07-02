package com.kosta.darfin.service.fund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.darfin.dto.fund.MarketOverviewDTO;
import com.kosta.darfin.websocket.StockWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class StockRankBroadcastScheduler {

    private final StockRankService stockRankService;
    private final MarketOverviewService marketOverviewService;
    private final StockWebSocketHandler stockWebSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 마지막으로 브로드캐스트한 payload 캐시 (신규 접속자에게 즉시 전송용)
    private volatile String lastPayload;

    // 투자자 동향·업종 순위 캐시 — 60초마다 갱신, RANK 브로드캐스트에 포함 (KIS rate limit 고려)
    private volatile List<Map<String, Object>> cachedInvestorSentiment;
    private volatile List<Map<String, Object>> cachedIndustries;


    public StockRankBroadcastScheduler(StockRankService stockRankService,
                                       MarketOverviewService marketOverviewService,
                                       StockWebSocketHandler stockWebSocketHandler) {
        this.stockRankService = stockRankService;
        this.marketOverviewService = marketOverviewService;
        this.stockWebSocketHandler = stockWebSocketHandler;
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
     * 60초마다 투자자 동향·업종 순위를 KIS API에서 갱신해 캐시에 저장.
     * broadcastRankData()의 10초 주기와 분리 — KIS rate limit(초당 20건) 보호.
     */
    @Scheduled(fixedDelay = 60000)
    public void refreshMarketExtraData() {
        try {
            cachedInvestorSentiment = stockRankService.getMarketInvestorSentiment();
            log.info("투자자 동향 캐시 갱신 완료");
        } catch (Exception e) {
            log.warn("투자자 동향 캐시 갱신 실패: {}", e.getMessage());
        }
        try {
            cachedIndustries = stockRankService.getIndustrySectorRanks();
            log.info("업종 순위 캐시 갱신 완료");
        } catch (Exception e) {
            log.warn("업종 순위 캐시 갱신 실패: {}", e.getMessage());
        }
    }

    // rank 코드 대량 구독 제거 — KIS WebSocket 구독 한도(OPSP0008) 방지.
    // 사용자가 상세 페이지 진입 시 addDetailCode()로만 동적 구독한다.

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

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "RANK");
        payload.put("marketOverview", getMarketOverviewOrNull());
        payload.put("tradeValue", ranks.getTradeValue());
        payload.put("volume", ranks.getVolume());
        payload.put("topGainers", ranks.getTopGainers());
        payload.put("topLosers", ranks.getTopLosers());
        payload.put("timestamp", System.currentTimeMillis());
        // 60초마다 갱신되는 캐시 — 없으면 포함하지 않음 (프론트가 초기 REST 응답 유지)
        if (cachedInvestorSentiment != null) payload.put("investorSentiment", cachedInvestorSentiment);
        if (cachedIndustries != null) payload.put("industries", cachedIndustries);

        return objectMapper.writeValueAsString(payload);
    }

    private MarketOverviewDTO getMarketOverviewOrNull() {
        try {
            return marketOverviewService.getMarketOverview();
        } catch (Exception e) {
            log.warn("시장 요약 조회 실패 — 랭크 브로드캐스트는 계속 진행: {}", e.getMessage());
            return null;
        }
    }
}
