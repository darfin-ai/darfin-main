package com.kosta.darfin.websocket;

import com.kosta.darfin.dto.fund.StockTickDTO;
import com.kosta.darfin.service.fund.KisRealtimeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP 세션 구독/해제를 추적해서
 *  1) /topic/detail/{code}/execution 또는 /orderbook 구독자가 생기면
 *     KIS 상세(체결+호가) 구독을 켜고, 둘 다 없어지면 끈다.
 *  2) /topic/price/{code} 구독 코드 전체 집합을 노출해 랭크 스케줄러가 실제 KIS 구독 대상을 계산할 때 쓴다.
 *  3) 연결된 세션 수를 노출해 "아무도 안 보고 있으면 KIS 호출 생략" 판단에 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompSubscriptionTracker {

    private static final Pattern DETAIL = Pattern.compile("^/topic/detail/([^/]+)/(?:execution|orderbook)$");
    private static final Pattern PRICE = Pattern.compile("^/topic/price/([^/]+)$");

    private final KisRealtimeClient kisRealtimeClient;
    private final SimpMessagingTemplate simpMessagingTemplate;

    // destination → 구독 중인 subscriptionKey(sessionId:subscriptionId) 집합
    private final Map<String, Set<String>> destinationSubscribers = new ConcurrentHashMap<>();
    // sessionId → (subscriptionId → destination) — 연결 종료 시 일괄 정리용
    private final Map<String, Map<String, String>> sessionSubscriptions = new ConcurrentHashMap<>();
    // 현재 연결된 STOMP 세션 id 집합
    private final Set<String> connectedSessions = ConcurrentHashMap.newKeySet();
    // execution/orderbook 중 하나라도 구독 중인 상세 종목 — 동시 구독 이벤트의 중복 시작/종료 방지
    private final Set<String> activeDetailCodes = ConcurrentHashMap.newKeySet();

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        String destination = accessor.getDestination();
        if (sessionId == null || subscriptionId == null || destination == null) return;

        connectedSessions.add(sessionId);
        sessionSubscriptions
                .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(subscriptionId, destination);

        boolean firstSubscriber = destinationSubscribers
                .computeIfAbsent(destination, k -> ConcurrentHashMap.newKeySet())
                .add(subscriptionKey(sessionId, subscriptionId));

        if (!firstSubscriber) return;
        if (destinationSubscribers.get(destination).size() > 1) return; // 이미 다른 세션이 구독 중

        Matcher detailMatcher = DETAIL.matcher(destination);
        if (detailMatcher.matches()) {
            String code = detailMatcher.group(1);
            if (activeDetailCodes.add(code)) {
                kisRealtimeClient.addDetailCode(code);
            }
            return;
        }

        Matcher priceMatcher = PRICE.matcher(destination);
        if (priceMatcher.matches()) {
            String code = priceMatcher.group(1);
            kisRealtimeClient.addPriceCode(code);
            StockTickDTO lastTick = kisRealtimeClient.getLastTick(code);
            if (lastTick != null) {
                simpMessagingTemplate.convertAndSend(destination, lastTick);
            }
        }
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        removeSubscription(accessor.getSessionId(), accessor.getSubscriptionId());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        connectedSessions.remove(sessionId);
        Map<String, String> subs = sessionSubscriptions.remove(sessionId);
        if (subs == null) return;
        subs.forEach((subscriptionId, destination) -> removeFromDestination(sessionId, subscriptionId, destination));
    }

    private void removeSubscription(String sessionId, String subscriptionId) {
        if (sessionId == null || subscriptionId == null) return;
        Map<String, String> subs = sessionSubscriptions.get(sessionId);
        String destination = subs != null ? subs.remove(subscriptionId) : null;
        if (destination == null) return;
        removeFromDestination(sessionId, subscriptionId, destination);
    }

    private void removeFromDestination(String sessionId, String subscriptionId, String destination) {
        Set<String> subscribers = destinationSubscribers.get(destination);
        if (subscribers == null) return;
        subscribers.remove(subscriptionKey(sessionId, subscriptionId));
        if (!subscribers.isEmpty()) return;

        destinationSubscribers.remove(destination);
        Matcher detailMatcher = DETAIL.matcher(destination);
        if (detailMatcher.matches()) {
            String code = detailMatcher.group(1);
            if (countDetailSubscribers(code) == 0 && activeDetailCodes.remove(code)) {
                kisRealtimeClient.removeDetailCode(code);
            }
            return;
        }

        Matcher priceMatcher = PRICE.matcher(destination);
        if (priceMatcher.matches()) {
            kisRealtimeClient.removePriceCode(priceMatcher.group(1));
        }
    }

    /** 현재 /topic/price/{code} 를 구독 중인 종목 코드 전체 (관심종목·보유종목 등). */
    public Set<String> getSubscribedPriceCodes() {
        Set<String> codes = ConcurrentHashMap.newKeySet();
        for (String destination : destinationSubscribers.keySet()) {
            Matcher m = PRICE.matcher(destination);
            if (m.matches()) codes.add(m.group(1));
        }
        return codes;
    }

    public boolean hasActiveSessions() {
        return !connectedSessions.isEmpty();
    }

    private int countDetailSubscribers(String code) {
        int count = 0;
        for (Map.Entry<String, Set<String>> entry : destinationSubscribers.entrySet()) {
            Matcher matcher = DETAIL.matcher(entry.getKey());
            if (matcher.matches() && code.equals(matcher.group(1))) {
                count += entry.getValue().size();
            }
        }
        return count;
    }

    private String subscriptionKey(String sessionId, String subscriptionId) {
        return sessionId + ":" + subscriptionId;
    }
}
