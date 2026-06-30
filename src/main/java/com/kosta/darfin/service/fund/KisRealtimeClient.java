package com.kosta.darfin.service.fund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.darfin.websocket.StockWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KisRealtimeClient {

    @Value("${kis.real.base-url}")
    private String baseUrl;

    @Value("${kis.real.websocket-url}")
    private String websocketUrl;

    @Value("${kis.real.app-key}")
    private String appKey;

    @Value("${kis.real.app-secret}")
    private String appSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StockWebSocketHandler stockWebSocketHandler;

    private WebSocketClient client;
    private String approvalKey;

    // 현재 KIS WebSocket에 구독 중인 종목 코드 목록
    private final Set<String> subscribedCodes = ConcurrentHashMap.newKeySet();

    public KisRealtimeClient(StockWebSocketHandler stockWebSocketHandler) {
        this.stockWebSocketHandler = stockWebSocketHandler;
    }

    @PostConstruct
    public void init() {
        connect();
    }

    /**
     * Approval Key 발급
     */
    private String getApprovalKey() {

        if (approvalKey != null) {
            return approvalKey;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", appKey);
        body.put("secretkey", appSecret);

        HttpEntity<Map<String, String>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        baseUrl + "/oauth2/Approval",
                        request,
                        String.class
                );

        try {

            JsonNode node = objectMapper.readTree(response.getBody());

            approvalKey = node.path("approval_key").asText();

            log.info("===== Approval Key 발급 성공 =====");

            return approvalKey;

        } catch (Exception e) {

            throw new RuntimeException("Approval Key 발급 실패", e);

        }

    }

    /**
     * KIS WebSocket 연결
     */
    public synchronized void connect() {

        if (client != null && client.isOpen()) {
            log.info("이미 연결되어 있습니다.");
            return;
        }

        String approval = getApprovalKey();

        log.info("Approval Key : {}", approval);

        try {

            log.info("===== KIS WebSocket Connect Start =====");

            client = new WebSocketClient(new URI(websocketUrl)) {

                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("===== KIS WebSocket OPEN =====");
                    if (subscribedCodes.isEmpty()) {
                        log.info("구독 종목 없음 — 랭크 스케줄러의 첫 번째 요청 대기 중");
                        return;
                    }
                    // 재연결 시 이전에 구독 중이던 종목 복원 (100ms 간격)
                    log.info("재구독 시작: {}개 종목", subscribedCodes.size());
                    for (String code : subscribedCodes) {
                        subscribe("H0STCNT0", code);
                        sleepQuietly(100);
                    }
                }

                @Override
                public void onMessage(String message) {
                    log.debug("KIS MSG: {}", message);
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {

                    log.warn("===== KIS CLOSED =====");
                    log.warn("code={} reason={} remote={}", code, reason, remote);

                }

                @Override
                public void onError(Exception ex) {

                    log.error("===== KIS ERROR =====", ex);

                }

            };

            client.connect();

        } catch (Exception e) {

            throw new RuntimeException(e);

        }

    }

    /**
     * 테스트용 메시지 전송
     */
    public void send(String message) {

        if (client != null && client.isOpen()) {

            client.send(message);

            log.info("SEND -> {}", message);

        } else {

            log.warn("WebSocket 미연결");

        }

    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    public void disconnect() {

        if (client != null) {
            client.close();
        }

    }

    @PreDestroy
    public void destroy() {
        disconnect();
    }

    /**
     * KIS WebSocket 수신 메시지 처리.
     * - JSON 형식: PINGPONG 또는 구독 응답 (헤더 파싱 후 PONG 처리)
     * - 파이프 구분 형식: 실시간 체결가 데이터 (0|H0STCNT0|001|필드들...)
     */
    private void handleMessage(String message) {
        if (message.startsWith("{")) {
            handleControlMessage(message);
        } else {
            handleRealtimeData(message);
        }
    }

    private void handleControlMessage(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String trId = node.path("header").path("tr_id").asText();

            if ("PINGPONG".equals(trId)) {
                client.send(json);
                log.debug("PINGPONG 응답 전송");
                return;
            }

            // 구독/해제 응답 — rt_cd가 0이 아니면 실패
            String rtCd  = node.path("body").path("rt_cd").asText();
            String msgCd = node.path("body").path("msg_cd").asText();
            String msg1  = node.path("body").path("msg1").asText();
            String trKey = node.path("header").path("tr_key").asText();

            if ("0".equals(rtCd)) {
                log.info("KIS 구독 응답 OK | tr_id={} tr_key={} msg={}", trId, trKey, msg1);
            } else {
                log.warn("KIS 구독 응답 실패 | tr_id={} tr_key={} rt_cd={} msg_cd={} msg={}", trId, trKey, rtCd, msgCd, msg1);
                // OPSP0008: 실제 구독 한도 초과 — 실패한 코드는 subscribedCodes에서 제거
                if ("OPSP0008".equals(msgCd)) {
                    subscribedCodes.remove(trKey);
                    log.warn("실제 KIS 구독 한도 도달 — {} 제거 (현재 구독 수: {})", trKey, subscribedCodes.size());
                }
            }
        } catch (Exception e) {
            log.warn("컨트롤 메시지 파싱 실패: {}", json);
        }
    }

    /**
     * KIS 실시간 체결 데이터 파싱 후 브라우저 클라이언트에 브로드캐스트.
     * 포맷: {암호화여부}|{tr_id}|{건수}|{필드1}^{필드2}^...
     *
     * H0STCNT0 주요 필드 순서 (0-based):
     *  0: 종목코드, 1: 체결시간, 2: 현재가, 3: 전일대비부호, 4: 전일대비, 5: 등락율
     */
    private void handleRealtimeData(String message) {
        String[] parts = message.split("\\|");
        if (parts.length < 4) return;

        String trId = parts[1];

        if ("H0STCNT0".equals(trId)) {
            String[] fields = parts[3].split("\\^");
            if (fields.length < 6) return;

            try {
                String code  = fields[0];
                long price   = Long.parseLong(fields[2]);
                double pct   = Double.parseDouble(fields[5]);

                Map<String, Object> data = new HashMap<>();
                data.put("type", "PRICE");
                data.put("code", code);
                data.put("price", price);
                data.put("pct", pct);

                stockWebSocketHandler.broadcast(objectMapper.writeValueAsString(data));
            } catch (Exception e) {
                log.error("실시간 체결 데이터 파싱 실패: {}", message, e);
            }
        }
    }

    // KIS WebSocket 동시 구독 한도 (실전 투자 기준)
    private static final int MAX_SUBSCRIPTIONS = 40;

    /**
     * 랭크 스케줄러가 주기적으로 호출. targetCodes와 현재 구독 목록을 비교해
     * 추가/삭제 분만 KIS WebSocket으로 요청한다.
     *
     * 주의: 구독 요청 사이에 100ms 딜레이를 둬야 KIS rate limit 을 피할 수 있다.
     * 딜레이 없이 한꺼번에 보내면 서버가 마지막 요청만 처리하고 나머지를 드랍한다.
     */
    public void syncSubscriptions(Set<String> targetCodes) {
        // 한도 초과 방지: 상위 MAX_SUBSCRIPTIONS개만 유지
        Set<String> limited = targetCodes.stream()
                .limit(MAX_SUBSCRIPTIONS)
                .collect(Collectors.toSet());

        if (!isConnected()) {
            subscribedCodes.clear();
            subscribedCodes.addAll(limited);
            return;
        }

        Set<String> toSubscribe = new HashSet<>(limited);
        toSubscribe.removeAll(subscribedCodes);

        Set<String> toUnsubscribe = new HashSet<>(subscribedCodes);
        toUnsubscribe.removeAll(limited);

        for (String code : toUnsubscribe) {
            unsubscribe("H0STCNT0", code);
            subscribedCodes.remove(code);
            sleepQuietly(100);
        }

        for (String code : toSubscribe) {
            subscribe("H0STCNT0", code);
            subscribedCodes.add(code);
            sleepQuietly(100);
        }

        if (!toSubscribe.isEmpty() || !toUnsubscribe.isEmpty()) {
            log.info("구독 동기화: +{}개 -{}개 (현재 {}개 / 한도 {}개)",
                    toSubscribe.size(), toUnsubscribe.size(), subscribedCodes.size(), MAX_SUBSCRIPTIONS);
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void subscribe(String trId, String code) {
        sendSubscribeMessage(trId, code, "1");
    }

    private void unsubscribe(String trId, String code) {
        sendSubscribeMessage(trId, code, "2");
    }

    private void sendSubscribeMessage(String trId, String code, String trType) {
        try {
            Map<String, Object> root = new HashMap<>();

            Map<String, String> header = new HashMap<>();
            header.put("approval_key", approvalKey);
            header.put("custtype", "P");
            header.put("tr_type", trType);
            header.put("content-type", "utf-8");

            Map<String, String> input = new HashMap<>();
            input.put("tr_id", trId);
            input.put("tr_key", code);

            Map<String, Object> body = new HashMap<>();
            body.put("input", input);

            root.put("header", header);
            root.put("body", body);

            client.send(objectMapper.writeValueAsString(root));
            log.debug("{} {} (tr_type={})", "1".equals(trType) ? "구독" : "구독해제", code, trType);
        } catch (Exception e) {
            log.error("sendSubscribeMessage error code={} trType={}", code, trType, e);
        }
    }

}