package com.kosta.darfin.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
@Component
public class StockWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // stockCode → 해당 종목 상세를 보는 세션들
    private final Map<String, Set<WebSocketSession>> detailSubscriptions = new ConcurrentHashMap<>();
    // sessionId → 구독 중인 stockCode (세션당 1개 상세 종목만)
    private final Map<String, String> sessionDetailCodes = new ConcurrentHashMap<>();

    // 순환 의존 방지: 콜백으로 스케줄러와 연결
    private Consumer<WebSocketSession> onConnectCallback;
    private BiConsumer<WebSocketSession, List<String>> onSubscribeCallback;
    private Consumer<String> onDetailSubscribeCallback;
    private Consumer<String> onDetailUnsubscribeCallback;

    public void setOnConnectCallback(Consumer<WebSocketSession> callback) {
        this.onConnectCallback = callback;
    }

    public void setOnSubscribeCallback(BiConsumer<WebSocketSession, List<String>> callback) {
        this.onSubscribeCallback = callback;
    }

    public void setOnDetailSubscribeCallback(Consumer<String> callback) {
        this.onDetailSubscribeCallback = callback;
    }

    public void setOnDetailUnsubscribeCallback(Consumer<String> callback) {
        this.onDetailUnsubscribeCallback = callback;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("웹소켓 연결 : {}", session.getId());

        if (onConnectCallback != null) {
            onConnectCallback.accept(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText();

            if ("SUBSCRIBE".equals(type) && onSubscribeCallback != null) {
                List<String> codes = new ArrayList<>();
                node.path("codes").forEach(c -> codes.add(c.asText()));
                if (!codes.isEmpty()) {
                    onSubscribeCallback.accept(session, codes);
                }
            } else if ("SUBSCRIBE_DETAIL".equals(type)) {
                String stockCode = node.path("code").asText();
                if (!stockCode.isEmpty()) {
                    handleDetailSubscribe(session, stockCode);
                }
            }
        } catch (Exception e) {
            log.warn("클라이언트 메시지 파싱 실패: {}", message.getPayload());
        }
    }

    private void handleDetailSubscribe(WebSocketSession session, String stockCode) {
        // 이전 상세 구독 해제
        String prevCode = sessionDetailCodes.remove(session.getId());
        if (prevCode != null && !prevCode.equals(stockCode)) {
            Set<WebSocketSession> prevSessions = detailSubscriptions.get(prevCode);
            if (prevSessions != null) {
                prevSessions.remove(session);
                if (prevSessions.isEmpty()) {
                    detailSubscriptions.remove(prevCode);
                    if (onDetailUnsubscribeCallback != null) {
                        onDetailUnsubscribeCallback.accept(prevCode);
                    }
                }
            }
        }
        // 새 상세 구독 등록
        detailSubscriptions.computeIfAbsent(stockCode, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionDetailCodes.put(session.getId(), stockCode);
        log.info("상세 구독: session={} code={}", session.getId(), stockCode);

        if (onDetailSubscribeCallback != null) {
            onDetailSubscribeCallback.accept(stockCode);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        // 상세 구독 정리
        String detailCode = sessionDetailCodes.remove(session.getId());
        if (detailCode != null) {
            Set<WebSocketSession> detailSessions = detailSubscriptions.get(detailCode);
            if (detailSessions != null) {
                detailSessions.remove(session);
                if (detailSessions.isEmpty()) {
                    detailSubscriptions.remove(detailCode);
                    if (onDetailUnsubscribeCallback != null) {
                        onDetailUnsubscribeCallback.accept(detailCode);
                    }
                }
            }
        }
        log.info("웹소켓 종료 : {}", session.getId());
    }

    public void broadcast(String message) {
        sessions.forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (Exception e) {
                log.error("브로드캐스트 실패 sessionId={} : {}", session.getId(), e.getMessage());
            }
        });
    }

    public void sendTo(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (Exception e) {
            log.error("개별 전송 실패 sessionId={} : {}", session.getId(), e.getMessage());
        }
    }

    /** 특정 종목 상세를 보는 세션들에만 메시지 전송 */
    public void sendToDetailSubscribers(String stockCode, String message) {
        Set<WebSocketSession> targets = detailSubscriptions.get(stockCode);
        if (targets == null || targets.isEmpty()) return;
        targets.forEach(session -> {
            try {
                if (session.isOpen()) session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                log.error("상세 전송 실패 sessionId={}: {}", session.getId(), e.getMessage());
            }
        });
    }

    /** 현재 상세 구독 중인 종목 코드 목록 */
    public Set<String> getDetailStockCodes() {
        return detailSubscriptions.keySet();
    }

    public boolean hasActiveSessions() {
        return !sessions.isEmpty();
    }

}