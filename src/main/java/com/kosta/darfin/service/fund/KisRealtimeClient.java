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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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

    // 현재 KIS WebSocket에 H0STCNT0(체결) 구독 중인 종목
    private final Set<String> subscribedCodes = ConcurrentHashMap.newKeySet();
    // 상세 페이지에서 보는 종목 — H0STCNT0 + H0STASP0 둘 다 구독
    private final Set<String> detailCodes = ConcurrentHashMap.newKeySet();
    // H0STASP0(호가) 구독 중인 종목 (상세 페이지 진입/이탈로만 관리)
    private final Set<String> aspSubscribedCodes = ConcurrentHashMap.newKeySet();
    // OPSP0008 수신 시 현재 syncSubscriptions 루프를 조기 종료시키는 플래그
    private final AtomicBoolean subscriptionLimitHit = new AtomicBoolean(false);

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
                    boolean hasAnything = !subscribedCodes.isEmpty() || !aspSubscribedCodes.isEmpty();
                    if (!hasAnything) {
                        log.info("구독 종목 없음 — 랭크 스케줄러의 첫 번째 요청 대기 중");
                        return;
                    }
                    // 재연결 시 H0STCNT0 복원
                    log.info("재구독 시작: H0STCNT0={}개 H0STASP0={}개", subscribedCodes.size(), aspSubscribedCodes.size());
                    for (String code : subscribedCodes) {
                        subscribe("H0STCNT0", code);
                        sleepQuietly(100);
                    }
                    // 재연결 시 H0STASP0(호가) 복원
                    for (String code : aspSubscribedCodes) {
                        subscribe("H0STASP0", code);
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
                // OPSP0008: 구독 한도 초과 — 실패 코드 제거 + syncSubscriptions 루프 조기 종료 신호
                if ("OPSP0008".equals(msgCd)) {
                    subscribedCodes.remove(trKey);
                    subscriptionLimitHit.set(true);
                    log.warn("KIS 구독 한도 도달 — {} 제거 (남은 구독: {})", trKey, subscribedCodes.size());
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
                String code    = fields[0];
                long price     = Long.parseLong(fields[2]);
                double pct     = Double.parseDouble(fields[5]);
                long quantity  = fields.length > 9 ? Long.parseLong(fields[9]) : 0L;
                String rawTime = fields.length > 1 ? fields[1] : "";  // HHMMSS
                String time    = rawTime.length() == 6
                        ? rawTime.substring(0, 2) + ":" + rawTime.substring(2, 4) + ":" + rawTime.substring(4, 6)
                        : rawTime;

                // 관심종목 목록 전체 브로드캐스트 (PRICE)
                Map<String, Object> priceData = new HashMap<>();
                priceData.put("type", "PRICE");
                priceData.put("code", code);
                priceData.put("price", price);
                priceData.put("pct", pct);
                stockWebSocketHandler.broadcast(objectMapper.writeValueAsString(priceData));

                // 상세 페이지 구독자에게만 EXECUTION 전송
                if (detailCodes.contains(code)) {
                    Map<String, Object> execData = new HashMap<>();
                    execData.put("type", "EXECUTION");
                    execData.put("code", code);
                    execData.put("price", price);
                    execData.put("quantity", quantity);
                    execData.put("changeRate", pct);
                    execData.put("time", time);
                    stockWebSocketHandler.sendToDetailSubscribers(code, objectMapper.writeValueAsString(execData));
                }
            } catch (Exception e) {
                log.error("실시간 체결 데이터 파싱 실패: {}", message, e);
            }

        } else if ("H0STASP0".equals(trId)) {
            // 실시간 호가 — fields[3~7]: 매도호가1~5, fields[8~12]: 매수호가1~5
            //               fields[13~17]: 매도잔량1~5, fields[18~22]: 매수잔량1~5
            String[] fields = parts[3].split("\\^");
            if (fields.length < 23) return;

            try {
                String code = fields[0];

                List<Map<String, Object>> asks = new ArrayList<>();
                List<Map<String, Object>> bids = new ArrayList<>();

                for (int i = 0; i < 5; i++) {
                    Map<String, Object> ask = new HashMap<>();
                    ask.put("price",    Long.parseLong(fields[3 + i]));
                    ask.put("quantity", Long.parseLong(fields[13 + i]));
                    asks.add(ask);

                    Map<String, Object> bid = new HashMap<>();
                    bid.put("price",    Long.parseLong(fields[8 + i]));
                    bid.put("quantity", Long.parseLong(fields[18 + i]));
                    bids.add(bid);
                }

                Map<String, Object> obData = new HashMap<>();
                obData.put("type", "ORDERBOOK");
                obData.put("code", code);
                obData.put("asks", asks);
                obData.put("bids", bids);

                stockWebSocketHandler.sendToDetailSubscribers(code, objectMapper.writeValueAsString(obData));
            } catch (Exception e) {
                log.error("실시간 호가 데이터 파싱 실패: {}", message, e);
            }
        }
    }

    // KIS WebSocket 동시 구독 한도 (실전 투자 기준)
    private static final int MAX_SUBSCRIPTIONS = 40;

    /** 상세 페이지 진입 시 호출 — H0STCNT0(체결) + H0STASP0(호가) 동시 구독 */
    public void addDetailCode(String stockCode) {
        detailCodes.add(stockCode);
        if (isConnected()) {
            if (!subscribedCodes.contains(stockCode)) {
                subscribe("H0STCNT0", stockCode);
                subscribedCodes.add(stockCode);
            }
            if (!aspSubscribedCodes.contains(stockCode)) {
                subscribe("H0STASP0", stockCode);
                aspSubscribedCodes.add(stockCode);
            }
            log.info("상세 구독 추가 (체결+호가): {}", stockCode);
        }
    }

    /** 상세 페이지 이탈 시 호출 — H0STCNT0 + H0STASP0 즉시 해제 */
    public void removeDetailCode(String stockCode) {
        detailCodes.remove(stockCode);
        if (isConnected()) {
            if (aspSubscribedCodes.remove(stockCode)) {
                unsubscribe("H0STASP0", stockCode);
            }
            if (subscribedCodes.remove(stockCode)) {
                unsubscribe("H0STCNT0", stockCode);
            }
        }
        log.info("상세 구독 해제 (체결+호가): {}", stockCode);
    }

    /**
     * 랭크 스케줄러가 주기적으로 호출. targetCodes와 현재 구독 목록을 비교해
     * 추가/삭제 분만 KIS WebSocket으로 요청한다.
     *
     * 주의: 구독 요청 사이에 100ms 딜레이를 둬야 KIS rate limit 을 피할 수 있다.
     * 딜레이 없이 한꺼번에 보내면 서버가 마지막 요청만 처리하고 나머지를 드랍한다.
     */
    public void syncSubscriptions(Set<String> targetCodes) {
        // 이전 sync에서 남은 한도 플래그 초기화
        subscriptionLimitHit.set(false);

        // detailCodes 포함하여 구독 대상 결정
        Set<String> combined = new HashSet<>(targetCodes);
        combined.addAll(detailCodes);

        // 한도 초과 방지: 상위 MAX_SUBSCRIPTIONS개만 유지
        Set<String> limited = combined.stream()
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

        int subscribed = 0;
        for (String code : toSubscribe) {
            // OPSP0008 수신 시 즉시 중단 — 나머지는 다음 sync 사이클로 미룸
            if (subscriptionLimitHit.get()) {
                log.warn("구독 한도 도달 — 남은 {} 코드 생략 (다음 sync에서 재시도)", toSubscribe.size() - subscribed);
                break;
            }
            subscribe("H0STCNT0", code);
            subscribedCodes.add(code);
            sleepQuietly(100);
            subscribed++;
        }

        if (subscribed > 0 || !toUnsubscribe.isEmpty()) {
            log.info("구독 동기화: +{}개 -{}개 (현재 {}개 / 한도 {}개)",
                    subscribed, toUnsubscribe.size(), subscribedCodes.size(), MAX_SUBSCRIPTIONS);
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