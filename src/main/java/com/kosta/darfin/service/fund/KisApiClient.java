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

import java.util.HashMap;
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

        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = kisBaseUrl
                + "/uapi/domestic-stock/v1/quotations/inquire-price"
                + "?FID_COND_MRKT_DIV_CODE=J"
                + "&FID_INPUT_ISCD=" + stockCode;

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class);

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