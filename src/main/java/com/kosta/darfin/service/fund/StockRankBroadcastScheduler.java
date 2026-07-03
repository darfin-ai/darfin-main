package com.kosta.darfin.service.fund;

import com.kosta.darfin.dto.fund.MarketOverviewDTO;
import com.kosta.darfin.dto.fund.StockSummaryDTO;
import com.kosta.darfin.websocket.StompSubscriptionTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StockRankBroadcastScheduler {

    private final StockRankService stockRankService;
    private final MarketOverviewService marketOverviewService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final StompSubscriptionTracker subscriptionTracker;
    private final KisRealtimeClient kisRealtimeClient;

    // 마지막으로 브로드캐스트한 payload 캐시 (신규 접속자에게 즉시 전송용)
    private volatile Map<String, Object> lastPayload;

    // 투자자 동향·업종 순위 캐시 — 60초마다 갱신, RANK 브로드캐스트에 포함 (KIS rate limit 고려)
    private volatile List<Map<String, Object>> cachedInvestorSentiment;
    private volatile List<Map<String, Object>> cachedIndustries;


    public StockRankBroadcastScheduler(StockRankService stockRankService,
                                       MarketOverviewService marketOverviewService,
                                       SimpMessagingTemplate simpMessagingTemplate,
                                       StompSubscriptionTracker subscriptionTracker,
                                       KisRealtimeClient kisRealtimeClient) {
        this.stockRankService = stockRankService;
        this.marketOverviewService = marketOverviewService;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.subscriptionTracker = subscriptionTracker;
        this.kisRealtimeClient = kisRealtimeClient;
    }

    /**
     * 10초마다 랭크 데이터를 갱신해서 /topic/rank 로 브로드캐스트.
     * 연결된 클라이언트가 없으면 KIS API 호출 자체를 생략한다.
     * 갱신 후, 4개 탭 종목 + 현재 /topic/price/{code} 구독 종목(관심종목 등)을 합쳐
     * KIS 실시간 WebSocket 구독을 동기화한다.
     */
    @Scheduled(fixedDelay = 10000)
    public void broadcastRankData() {
        if (!subscriptionTracker.hasActiveSessions()) {
            return;
        }

        try {
            StockRankService.AllRankResult ranks = stockRankService.getAllRanks();

            Map<String, Object> payload = buildRankPayload(ranks);
            lastPayload = payload;
            simpMessagingTemplate.convertAndSend("/topic/rank", payload);
            log.info("랭크 브로드캐스트 완료");

            syncRealtimeSubscriptions(ranks);
        } catch (Exception e) {
            log.error("랭크 브로드캐스트 실패", e);
        }
    }

    /** 순위표 4탭 종목 + 관심종목 등 /topic/price 구독 종목을 합쳐 KIS 실시간 구독을 갱신한다. */
    private void syncRealtimeSubscriptions(StockRankService.AllRankResult ranks) {
        Set<String> targetCodes = new HashSet<>();
        collectCodes(targetCodes, ranks.getTradeValue());
        collectCodes(targetCodes, ranks.getVolume());
        collectCodes(targetCodes, ranks.getTopGainers());
        collectCodes(targetCodes, ranks.getTopLosers());
        targetCodes.addAll(subscriptionTracker.getSubscribedPriceCodes());

        kisRealtimeClient.syncSubscriptions(targetCodes);
    }

    private void collectCodes(Set<String> target, List<StockSummaryDTO> stocks) {
        target.addAll(stocks.stream().map(StockSummaryDTO::getCode).collect(Collectors.toSet()));
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

    /** /topic/rank 신규 구독자에게 캐시된 최신 랭크를 즉시 전송 (StompSubscriptionTracker가 호출). */
    public Map<String, Object> getLastPayload() {
        return lastPayload;
    }

    private Map<String, Object> buildRankPayload(StockRankService.AllRankResult ranks) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tradeValue", ranks.getTradeValue());
        payload.put("volume", ranks.getVolume());
        payload.put("topGainers", ranks.getTopGainers());
        payload.put("topLosers", ranks.getTopLosers());
        payload.put("marketOverview", getMarketOverviewOrNull());
        payload.put("timestamp", System.currentTimeMillis());
        // 60초마다 갱신되는 캐시 — 없으면 포함하지 않음 (프론트가 초기 REST 응답 유지)
        if (cachedInvestorSentiment != null) payload.put("investorSentiment", cachedInvestorSentiment);
        if (cachedIndustries != null) payload.put("industries", cachedIndustries);

        return payload;
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
