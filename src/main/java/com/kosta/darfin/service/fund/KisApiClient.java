package com.kosta.darfin.service.fund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.kosta.darfin.dto.fund.ExecutionResponse;
import com.kosta.darfin.dto.fund.OrderBookResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * KIS Open API 모의투자 환경 클라이언트.
 * Spring Boot 2.7.18 기준이라 RestClient(3.2+ 전용) 대신 RestTemplate 사용.
 * TR_ID, 도메인은 모의투자(openapivts) 기준.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KisApiClient {

    @Value("${kis.mock.app-key}")
    private String appKey;

    @Value("${kis.mock.app-secret}")
    private String appSecret;

    @Value("${kis.mock.base-url}")
    private String kisBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicReference<String> cachedToken = new AtomicReference<>();
    private final AtomicLong tokenExpiryEpochMs = new AtomicLong(0);

    // KIS mock API rate limit: 250ms 간격 (호출 간 최소 대기)
    private final Object callLock = new Object();
    private volatile long lastCallTime = 0;
    private static final long CALL_INTERVAL_MS = 250;

    private void throttle() {
        synchronized (callLock) {
            long now = System.currentTimeMillis();
            long wait = (lastCallTime + CALL_INTERVAL_MS) - now;
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            lastCallTime = System.currentTimeMillis();
        }
    }

    /**
     * EGW00201(초당 거래건수 초과) 발생 시 1초 대기 후 1회 재시도.
     * 모의투자 API는 rate limit 창이 짧아 짧은 대기만으로 해소된다.
     */
    private ResponseEntity<String> exchangeWithRetry(String url, HttpHeaders headers) {
        HttpEntity<Void> req = new HttpEntity<>(headers);
        try {
            return restTemplate.exchange(url, HttpMethod.GET, req, String.class);
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            if (e.getResponseBodyAsString().contains("EGW00201")) {
                log.warn("EGW00201 — 1초 대기 후 재시도: {}", url);
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                throttle();
                return restTemplate.exchange(url, HttpMethod.GET, req, String.class);
            }
            throw e;
        }
    }

    /** 토큰 발급 — 23시간 캐싱 */
    private String getToken() {
        if (cachedToken.get() != null && System.currentTimeMillis() < tokenExpiryEpochMs.get()) {
            return cachedToken.get();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", appKey);
        body.put("appsecret", appSecret);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                kisBaseUrl + "/oauth2/tokenP",
                request,
                String.class
        );

        try {
            JsonNode node = objectMapper.readTree(response.getBody());
            String token = node.path("access_token").asText();
            cachedToken.set(token);
            tokenExpiryEpochMs.set(System.currentTimeMillis() + 23L * 60 * 60 * 1000);
            log.info("KIS 토큰 발급 완료");
            return token;
        } catch (Exception e) {
            throw new RuntimeException("KIS 토큰 응답 파싱 실패: " + response.getBody(), e);
        }
    }

    /** 종목 기본 정보 + 현재가 조회 (TR_ID: FHKST01010100) */
    public StockBasicInfo fetchStockBasicInfo(String stockCode) {
        throttle();
        String token = getToken();

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST01010100");

        String url = kisBaseUrl
                + "/uapi/domestic-stock/v1/quotations/inquire-price"
                + "?FID_COND_MRKT_DIV_CODE=J"
                + "&FID_INPUT_ISCD=" + stockCode;

        ResponseEntity<String> response = exchangeWithRetry(url, headers);

        try {
            JsonNode o = objectMapper.readTree(response.getBody()).path("output");

            return StockBasicInfo.builder()
                    .stockCode(stockCode)
                    .stockName(o.path("hts_kor_isnm").asText())     // 모의투자 응답엔 보통 빈값 → stock 테이블로 보완
                    .market(o.path("rprs_mrkt_kor_name").asText())  // 예: "KOSPI200"
                    .sector(o.path("bstp_kor_isnm").asText())       // 예: "전기·전자"
                    .marketCap(o.path("hts_avls").asLong(0) * 100_000_000L)
                    .per(o.path("per").asDouble(0))
                    .pbr(o.path("pbr").asDouble(0))
                    .currentPrice(o.path("stck_prpr").asLong(0))
                    .high52w(o.path("w52_hgpr").asLong(0))
                    .low52w(o.path("w52_lwpr").asLong(0))
                    .changeRate(o.path("prdy_ctrt").asDouble(0))
                    .tradeValue(o.path("acml_tr_pbmn").asLong(0))
                    .volume(o.path("acml_vol").asLong(0))
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("KIS 현재가 응답 파싱 실패: " + response.getBody(), e);
        }
    }

    /** 호가 조회 (TR_ID: FHKST01010200) — 매도/매수 각 5호가 */
    public OrderBookResponse fetchOrderBook(String stockCode) {
        throttle();
        String token = getToken();

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST01010200");

        String url = kisBaseUrl
                + "/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn"
                + "?FID_COND_MRKT_DIV_CODE=J"
                + "&FID_INPUT_ISCD=" + stockCode;

        ResponseEntity<String> response = exchangeWithRetry(url, headers);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode o1 = root.path("output1");

            long currentPrice = o1.path("stck_prpr").asLong(0);
            double changeRate  = o1.path("prdy_ctrt").asDouble(0);

            List<OrderBookResponse.OrderItem> asks = new ArrayList<>();
            List<OrderBookResponse.OrderItem> bids = new ArrayList<>();

            for (int i = 1; i <= 5; i++) {
                asks.add(OrderBookResponse.OrderItem.builder()
                        .price(o1.path("askp" + i).asLong(0))
                        .quantity(o1.path("askp_rsqn" + i).asLong(0))
                        .build());
                bids.add(OrderBookResponse.OrderItem.builder()
                        .price(o1.path("bidp" + i).asLong(0))
                        .quantity(o1.path("bidp_rsqn" + i).asLong(0))
                        .build());
            }

            return OrderBookResponse.builder()
                    .currentPrice(currentPrice)
                    .changeRate(changeRate)
                    .asks(asks)
                    .bids(bids)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("KIS 호가 응답 파싱 실패: " + response.getBody(), e);
        }
    }

    /** 최근 체결 목록 조회 (TR_ID: FHKST01010300) */
    public List<ExecutionResponse> fetchRecentExecutions(String stockCode) {
        throttle();
        String token = getToken();

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST01010300");

        String url = kisBaseUrl
                + "/uapi/domestic-stock/v1/quotations/inquire-ccnl"
                + "?FID_COND_MRKT_DIV_CODE=J"
                + "&FID_INPUT_ISCD=" + stockCode;

        ResponseEntity<String> response = exchangeWithRetry(url, headers);

        try {
            JsonNode output = objectMapper.readTree(response.getBody()).path("output");
            List<ExecutionResponse> result = new ArrayList<>();

            for (JsonNode item : output) {
                String raw = item.path("stck_cntg_hour").asText("");  // HHMMSS
                String time = raw.length() == 6
                        ? raw.substring(0, 2) + ":" + raw.substring(2, 4) + ":" + raw.substring(4, 6)
                        : raw;

                result.add(ExecutionResponse.builder()
                        .price(item.path("stck_prpr").asLong(0))
                        .quantity(item.path("cntg_vol").asLong(0))
                        .changeRate(item.path("prdy_ctrt").asDouble(0))
                        .time(time)
                        .build());
            }
            return result;

        } catch (Exception e) {
            throw new RuntimeException("KIS 체결 응답 파싱 실패: " + response.getBody(), e);
        }
    }

    @Getter
    @Builder
    public static class StockBasicInfo {
        private String stockCode;
        private String stockName;
        private String market;
        private String sector;
        private Long marketCap;
        private Double per;
        private Double pbr;
        private Long currentPrice;
        private Long high52w;
        private Long low52w;
        private Double changeRate;
        private Long tradeValue;
        private Long volume;
    }
}