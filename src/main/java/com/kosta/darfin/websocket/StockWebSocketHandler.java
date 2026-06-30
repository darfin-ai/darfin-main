package com.kosta.darfin.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
@Component
public class StockWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 순환 의존 방지: 콜백으로 스케줄러와 연결
    private Consumer<WebSocketSession> onConnectCallback;
    private BiConsumer<WebSocketSession, List<String>> onSubscribeCallback;

    public void setOnConnectCallback(Consumer<WebSocketSession> callback) {
        this.onConnectCallback = callback;
    }

    public void setOnSubscribeCallback(BiConsumer<WebSocketSession, List<String>> callback) {
        this.onSubscribeCallback = callback;
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
            if ("SUBSCRIBE".equals(node.path("type").asText()) && onSubscribeCallback != null) {
                List<String> codes = new ArrayList<>();
                node.path("codes").forEach(c -> codes.add(c.asText()));
                if (!codes.isEmpty()) {
                    onSubscribeCallback.accept(session, codes);
                }
            }
        } catch (Exception e) {
            log.warn("클라이언트 메시지 파싱 실패: {}", message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
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

    public boolean hasActiveSessions() {
        return !sessions.isEmpty();
    }

}