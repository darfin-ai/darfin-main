package com.kosta.darfin.service.fund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * KIS 실전투자 도메인 — 순위분석 API 전용 클라이언트.
 * volume-rank API는 모의투자 미지원이라 실전 도메인을 따로 써야 한다.
 *
 * 중요: 이 클라이언트는 "조회 전용" 순위 API만 호출한다.
 * 매수/매도 등 실거래 주문에는 절대 사용하지 않는다 (그건 모의투자 KisApiClient가 전담).
 *
 * 실전 Rate Limit: 초당 20건 (모의투자보다 훨씬 여유로움).
 * 4개 탭(거래대금/거래량/급상승/급하락) × 1회 호출 = 총 4건/주기 이므로 제한과 무관.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KisRankApiClient {

    @Value("${kis.real.app-key}")
    private String realAppKey;

    @Value("${kis.real.app-secret}")
    private String realAppSecret;

    private static final String KIS_REAL_BASE = "https://openapi.koreainvestment.com:9443";
    private static final String TR_ID_VOLUME_RANK = "FHPST01710000";
    private static final String TR_ID_INDEX_PRICE = "FHPUP02100000";
    private static final String TR_ID_INDEX_DAILY_CHART = "FHPUP02120000";
    private static final String TR_ID_INDEX_INTRADAY = "FHKUP03500200";
    private static final String TR_ID_OVERSEAS_DAILY_CHART = "FHKST03030100";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicReference<String> cachedRealToken = new AtomicReference<>();
    private final AtomicLong realTokenExpiryEpochMs = new AtomicLong(0);

    // KIS 실전 API 전역 호출 간격 — 캔들/순위 등 모든 REST 호출이 이 게이트를 통과한다.
    // 초당 20건 제한이지만 다른 스레드와 공유하므로 보수적으로 600ms 고정
    private static final long MIN_CALL_INTERVAL_MS = 900;
    private final AtomicLong lastKisCallMs = new AtomicLong(0);

    /** 실전 도메인 토큰 발급 — 모의투자 토큰과 완전히 별개로 캐싱 (23시간) */
    private String getRealToken() {
        if (cachedRealToken.get() != null && System.currentTimeMillis() < realTokenExpiryEpochMs.get()) {
            return cachedRealToken.get();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", realAppKey);
        body.put("appsecret", realAppSecret);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                KIS_REAL_BASE + "/oauth2/tokenP", request, String.class);

        try {
            JsonNode node = objectMapper.readTree(response.getBody());
            String token = node.path("access_token").asText();
            cachedRealToken.set(token);
            realTokenExpiryEpochMs.set(System.currentTimeMillis() + 23L * 60 * 60 * 1000);
            log.info("KIS 실전 토큰 발급 완료 (순위조회 전용)");
            return token;
        } catch (Exception e) {
            throw new RuntimeException("KIS 실전 토큰 응답 파싱 실패: " + response.getBody(), e);
        }
    }

    /**
     * 거래량순위 API 공통 호출.
     * FID_BLNG_CLS_CODE 값으로 정렬 기준(거래량/거래증가율/평균거래전율/거래금액순)을 바꾸고,
     * 응답을 등락률 기준으로 재정렬하면 급상승/급하락도 같은 API로 처리 가능하다.
     *
     * @param sortType 0:평균거래량 1:거래증가율 2:평균거래회전율 3:거래금액순 4:평균거래금액회전율
     */
    public List<RankItem> fetchVolumeRank(String sortType) {
        throttleKisCall();
        String token = getRealToken();

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", realAppKey);
        headers.set("appsecret", realAppSecret);
        headers.set("tr_id", TR_ID_VOLUME_RANK);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = KIS_REAL_BASE + "/uapi/domestic-stock/v1/quotations/volume-rank"
                + "?FID_COND_MRKT_DIV_CODE=J"
                + "&FID_COND_SCR_DIV_CODE=20171"
                + "&FID_INPUT_ISCD=0000"
                + "&FID_DIV_CLS_CODE=0"
                + "&FID_BLNG_CLS_CODE=" + sortType
                + "&FID_TRGT_CLS_CODE=111111111"
                + "&FID_TRGT_EXLS_CLS_CODE=0000000000"
                + "&FID_INPUT_PRICE_1="
                + "&FID_INPUT_PRICE_2="
                + "&FID_VOL_CNT=";

        ResponseEntity<String> response = exchangeGetWithRetry(url, request);

        try {
            JsonNode output = objectMapper.readTree(response.getBody()).path("output");
            List<RankItem> result = new ArrayList<>();

            if (output.isArray()) {
                for (JsonNode item : output) {
                    result.add(RankItem.builder()
                            .stockCode(item.path("mksc_shrn_iscd").asText())
                            .stockName(item.path("hts_kor_isnm").asText())
                            .currentPrice(item.path("stck_prpr").asLong(0))
                            .changeRate(item.path("prdy_ctrt").asDouble(0))
                            .tradeValue(item.path("acml_tr_pbmn").asLong(0))
                            .volume(item.path("acml_vol").asLong(0))
                            .build());
                }
            }
            return result;

        } catch (Exception e) {
            throw new RuntimeException("KIS 순위 응답 파싱 실패: " + response.getBody(), e);
        }
    }

    /** 국내업종 현재지수 조회. KOSPI=0001, KOSDAQ=1001 */
    public MarketIndexItem fetchMarketIndex(String indexCode, String indexName) {
        throttleKisCall();
        String token = getRealToken();

        HttpHeaders headers = realApiHeaders(token, TR_ID_INDEX_PRICE);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = KIS_REAL_BASE
                + "/uapi/domestic-stock/v1/quotations/inquire-index-price"
                + "?FID_COND_MRKT_DIV_CODE=U"
                + "&FID_INPUT_ISCD=" + indexCode;

        ResponseEntity<String> response = exchangeGetWithRetry(url, request);

        try {
            JsonNode output = objectMapper.readTree(response.getBody()).path("output");
            return MarketIndexItem.builder()
                    .code(indexCode)
                    .name(indexName)
                    .price(readDouble(output, "bstp_nmix_prpr", "stck_prpr", "ovrs_nmix_prpr"))
                    .change(readDouble(output, "bstp_nmix_prdy_vrss", "prdy_vrss"))
                    .changeRate(readDouble(output, "bstp_nmix_prdy_ctrt", "prdy_ctrt"))
                    .volume(readLong(output, "acml_vol", "acml_vol_lwpr"))
                    .tradeValue(readLong(output, "acml_tr_pbmn", "acml_tr_pbmn_lwpr"))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("KIS 지수 응답 파싱 실패: " + response.getBody(), e);
        }
    }

    /**
     * USD/KRW 환율 조회.
     * "해외주식 현재가 시세"(HHDFS00000300)는 EXCD가 NAS/NYS 등 3자리 거래소 코드 전용이라
     * 환율(FX_IDC)엔 애초에 쓸 수 없다(WRONG VALUE SIZE [EXCD] 에러) — 대신 지수/환율 겸용
     * 기간별시세(FHKST03030100, FID_COND_MRKT_DIV_CODE=X, FID_INPUT_ISCD=FX@KRW)의
     * output1(당일 요약)에서 현재가를 읽는다. 실계좌로 검증한 필드명.
     */
    public ExchangeRateItem fetchUsdKrwExchangeRate() {
        throttleKisCall();
        String token = getRealToken();

        HttpHeaders headers = realApiHeaders(token, TR_ID_OVERSEAS_DAILY_CHART);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        LocalDate today = LocalDate.now();
        String url = KIS_REAL_BASE
                + "/uapi/overseas-price/v1/quotations/inquire-daily-chartprice"
                + "?FID_COND_MRKT_DIV_CODE=X"
                + "&FID_INPUT_ISCD=FX@KRW"
                + "&FID_INPUT_DATE_1=" + today.minusDays(7).format(DATE_FMT)
                + "&FID_INPUT_DATE_2=" + today.format(DATE_FMT)
                + "&FID_PERIOD_DIV_CODE=D";

        ResponseEntity<String> response = exchangeGetWithRetry(url, request);

        try {
            JsonNode output1 = objectMapper.readTree(response.getBody()).path("output1");

            double rate = readDouble(output1, "ovrs_nmix_prpr");
            double change = readDouble(output1, "ovrs_nmix_prdy_vrss");
            double changeRate = readDouble(output1, "prdy_ctrt");

            if (rate == 0) {
                log.warn("KIS 환율 응답 필드 확인 필요: {}", response.getBody());
            }

            return ExchangeRateItem.builder()
                    .currency("USD/KRW")
                    .name("달러환율")
                    .rate(rate)
                    .change(change)
                    .changeRate(changeRate)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("KIS 환율 응답 파싱 실패: " + response.getBody(), e);
        }
    }

    private HttpHeaders realApiHeaders(String token, String trId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", realAppKey);
        headers.set("appsecret", realAppSecret);
        headers.set("tr_id", trId);
        return headers;
    }

    private ResponseEntity<String> exchangeGetWithRetry(String url, HttpEntity<Void> request) {
        int attempts = 0;
        while (true) {
            try {
                return restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            } catch (HttpStatusCodeException e) {
                attempts++;
                if (attempts >= 3 || !e.getResponseBodyAsString().contains("EGW00201")) {
                    throw e;
                }
                log.warn("KIS rate limit(EGW00201) 발생 — {}번째 재시도 대기", attempts);
                sleepQuietly(1200L * attempts);
                throttleKisCall();
            }
        }
    }

    private String readText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.asText("").isEmpty()) {
                return value.asText();
            }
        }
        return "";
    }

    private double readDouble(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.asText("").isEmpty()) {
                return parseDouble(value.asText());
            }
        }
        return 0;
    }

    private long readLong(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.asText("").isEmpty()) {
                return parseLong(value.asText());
            }
        }
        return 0;
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.replace(",", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 국내주식 일봉 조회 (TR_ID: FHKST03010100).
     * KIS는 요청당 최대 100개 반환 → 2회 호출로 최대 200개(~10개월치) 취합.
     * 결과는 오래된 순(ascending) 정렬로 반환한다.
     */
    public List<CandleData> fetchDailyCandles(String stockCode) {
        LocalDate today = LocalDate.now();

        // 최근 5개월치
        List<CandleData> recent = fetchCandleBatch(stockCode,
                today.minusMonths(5).format(DATE_FMT),
                today.format(DATE_FMT));

        sleepQuietly(300);

        // 그 이전 5개월치
        List<CandleData> older = fetchCandleBatch(stockCode,
                today.minusMonths(10).format(DATE_FMT),
                today.minusMonths(5).minusDays(1).format(DATE_FMT));

        List<CandleData> all = new ArrayList<>(older.size() + recent.size());
        all.addAll(older);
        all.addAll(recent);
        return all;
    }

    private List<CandleData> fetchCandleBatch(String stockCode, String from, String to) {
        throttleKisCall();
        String token = getRealToken();

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", realAppKey);
        headers.set("appsecret", realAppSecret);
        headers.set("tr_id", "FHKST03010100");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = KIS_REAL_BASE
                + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
                + "?FID_COND_MRKT_DIV_CODE=J"
                + "&FID_INPUT_ISCD=" + stockCode
                + "&FID_INPUT_DATE_1=" + from
                + "&FID_INPUT_DATE_2=" + to
                + "&FID_PERIOD_DIV_CODE=D"
                + "&FID_ORG_ADJ_PRC=0";

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            JsonNode output2 = objectMapper.readTree(response.getBody()).path("output2");
            List<CandleData> result = new ArrayList<>();

            if (output2.isArray()) {
                for (JsonNode item : output2) {
                    String date = item.path("stck_bsop_date").asText();
                    if (date.isEmpty() || date.equals("null")) continue;
                    result.add(new CandleData(
                            date,
                            item.path("stck_oprc").asLong(0),
                            item.path("stck_hgpr").asLong(0),
                            item.path("stck_lwpr").asLong(0),
                            item.path("stck_clpr").asLong(0),
                            item.path("acml_vol").asLong(0)
                    ));
                }
            }
            // KIS는 최신→과거 순으로 반환하므로 역정렬
            Collections.reverse(result);
            return result;

        } catch (Exception e) {
            log.warn("일봉 조회 실패 code={} from={} to={}: {}", stockCode, from, to, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 국내업종 일자별 지수 조회 (KOSPI=0001, KOSDAQ=1001). TR_ID: FHPUP02120000.
     * 종목 일봉과 동일하게 2회 호출로 최대 200개(~10개월치) 취합, 오래된 순 정렬.
     */
    public List<IndexCandleData> fetchIndexDailyCandles(String indexCode) {
        LocalDate today = LocalDate.now();

        List<IndexCandleData> recent = fetchIndexCandleBatch(indexCode,
                today.minusMonths(5).format(DATE_FMT), today.format(DATE_FMT));
        sleepQuietly(300);
        List<IndexCandleData> older = fetchIndexCandleBatch(indexCode,
                today.minusMonths(10).format(DATE_FMT), today.minusMonths(5).minusDays(1).format(DATE_FMT));

        List<IndexCandleData> all = new ArrayList<>(older.size() + recent.size());
        all.addAll(older);
        all.addAll(recent);
        return all;
    }

    private List<IndexCandleData> fetchIndexCandleBatch(String indexCode, String from, String to) {
        throttleKisCall();
        String token = getRealToken();
        HttpHeaders headers = realApiHeaders(token, TR_ID_INDEX_DAILY_CHART);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = KIS_REAL_BASE
                + "/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice"
                + "?FID_COND_MRKT_DIV_CODE=U"
                + "&FID_INPUT_ISCD=" + indexCode
                + "&FID_INPUT_DATE_1=" + from
                + "&FID_INPUT_DATE_2=" + to
                + "&FID_PERIOD_DIV_CODE=D";

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            JsonNode output2 = objectMapper.readTree(response.getBody()).path("output2");
            List<IndexCandleData> result = new ArrayList<>();
            if (output2.isArray()) {
                for (JsonNode item : output2) {
                    String date = item.path("stck_bsop_date").asText();
                    if (date.isEmpty() || date.equals("null")) continue;
                    result.add(new IndexCandleData(
                            date,
                            readDouble(item, "bstp_nmix_oprc"),
                            readDouble(item, "bstp_nmix_hgpr"),
                            readDouble(item, "bstp_nmix_lwpr"),
                            readDouble(item, "bstp_nmix_prpr"),
                            item.path("acml_vol").asLong(0)
                    ));
                }
            }
            Collections.reverse(result); // KIS는 최신→과거 순, 역정렬
            return result;
        } catch (Exception e) {
            log.warn("지수 일봉 조회 실패 code={} from={} to={}: {}", indexCode, from, to, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 지수 당일 분봉 조회 (KOSPI=0001, KOSDAQ=1001). TR_ID: FHKUP03500200.
     * 1분 간격, 오늘 장중 데이터만(FID_PW_DATA_INCU_YN=N) — 미니 차트용 "오늘 흐름" 표시에 사용.
     * 환율(FX)은 같은 계열 API(FHKST03030200)로 시도해도 이 계정 권한에서는 분봉이 비어 와서 지원하지 않는다.
     */
    public List<IndexCandleData> fetchIndexIntradayCandles(String indexCode) {
        throttleKisCall();
        String token = getRealToken();
        HttpHeaders headers = realApiHeaders(token, TR_ID_INDEX_INTRADAY);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = KIS_REAL_BASE
                + "/uapi/domestic-stock/v1/quotations/inquire-time-indexchartprice"
                + "?FID_COND_MRKT_DIV_CODE=U"
                + "&FID_ETC_CLS_CODE=0"
                + "&FID_INPUT_ISCD=" + indexCode
                + "&FID_INPUT_HOUR_1=60"
                + "&FID_PW_DATA_INCU_YN=N";

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            JsonNode output2 = objectMapper.readTree(response.getBody()).path("output2");
            List<IndexCandleData> result = new ArrayList<>();
            if (output2.isArray()) {
                for (JsonNode item : output2) {
                    String time = item.path("stck_cntg_hour").asText();
                    if (time.isEmpty() || time.equals("null")) continue;
                    result.add(new IndexCandleData(
                            time,
                            readDouble(item, "bstp_nmix_oprc"),
                            readDouble(item, "bstp_nmix_hgpr"),
                            readDouble(item, "bstp_nmix_lwpr"),
                            readDouble(item, "bstp_nmix_prpr"),
                            item.path("cntg_vol").asLong(0)
                    ));
                }
            }
            Collections.reverse(result); // KIS는 최신→과거 순, 역정렬
            return result;
        } catch (Exception e) {
            log.warn("지수 분봉 조회 실패 code={}: {}", indexCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * USD/KRW 환율 일자별 시세 조회. TR_ID: FHKST03030100 (해외지수/환율 기간별시세 공용 엔드포인트).
     * FID_COND_MRKT_DIV_CODE=X(환율), FID_INPUT_ISCD=USDKRW.
     * 응답 필드명은 계정/권한별로 달라질 수 있어(fetchUsdKrwExchangeRate와 동일 이슈) 후보 필드를 순서대로 읽는다.
     */
    public List<IndexCandleData> fetchExchangeRateDailyCandles() {
        LocalDate today = LocalDate.now();

        List<IndexCandleData> recent = fetchExchangeCandleBatch(
                today.minusMonths(5).format(DATE_FMT), today.format(DATE_FMT));
        sleepQuietly(300);
        List<IndexCandleData> older = fetchExchangeCandleBatch(
                today.minusMonths(10).format(DATE_FMT), today.minusMonths(5).minusDays(1).format(DATE_FMT));

        List<IndexCandleData> all = new ArrayList<>(older.size() + recent.size());
        all.addAll(older);
        all.addAll(recent);
        return all;
    }

    private List<IndexCandleData> fetchExchangeCandleBatch(String from, String to) {
        throttleKisCall();
        String token = getRealToken();
        HttpHeaders headers = realApiHeaders(token, TR_ID_OVERSEAS_DAILY_CHART);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = KIS_REAL_BASE
                + "/uapi/overseas-price/v1/quotations/inquire-daily-chartprice"
                + "?FID_COND_MRKT_DIV_CODE=X"
                + "&FID_INPUT_ISCD=FX@KRW"
                + "&FID_INPUT_DATE_1=" + from
                + "&FID_INPUT_DATE_2=" + to
                + "&FID_PERIOD_DIV_CODE=D";

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            JsonNode output2 = objectMapper.readTree(response.getBody()).path("output2");
            List<IndexCandleData> result = new ArrayList<>();
            if (output2.isArray()) {
                for (JsonNode item : output2) {
                    String date = readText(item, "stck_bsop_date", "xymd", "zdiv");
                    if (date.isEmpty() || date.equals("null")) continue;
                    result.add(new IndexCandleData(
                            date,
                            readDouble(item, "ovrs_nmix_oprc", "open"),
                            readDouble(item, "ovrs_nmix_hgpr", "high"),
                            readDouble(item, "ovrs_nmix_lwpr", "low"),
                            readDouble(item, "ovrs_nmix_prpr", "clos", "last"),
                            readLong(item, "acml_vol", "tvol", "gvol")
                    ));
                }
            }
            if (result.isEmpty()) {
                log.warn("환율 일봉 응답 필드 확인 필요: {}", response.getBody());
            }
            Collections.reverse(result); // KIS는 최신→과거 순, 역정렬
            return result;
        } catch (Exception e) {
            log.warn("환율 일봉 조회 실패 from={} to={}: {}", from, to, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 주봉용 시봉 조회 (TR_ID: FHKST03010200, FID_PW_DATA_INCU_YN=Y).
     * 8 배치로 복수 거래일 분봉을 취합 후 1시간봉으로 집계.
     * CandleData.date 필드: YYYYMMDDHH (10자) 형식.
     */
    public List<CandleData> fetchWeeklyIntradayCandles(String stockCode) {
        List<List<CandleData>> batches = new ArrayList<>();
        String endTime = currentEndTime();

        for (int i = 0; i < 8; i++) {
            List<CandleData> batch = fetchIntradayBatchWithDate(stockCode, endTime);
            if (batch.isEmpty()) break;
            batches.add(batch);

            // batch는 oldest-first이므로 get(0)이 가장 오래된 항목
            String oldest = batch.get(0).getDate(); // YYYYMMDDHHMMSS (14자)
            String timeOnly = oldest.length() >= 14 ? oldest.substring(8, 14) : oldest;
            endTime = subtractOneMinute(timeOnly);
            if (endTime.compareTo("090000") <= 0) break;

            if (i < 7) sleepQuietly(300);
        }

        // 배치 순서를 뒤집어 오래된 것부터 합산 (dedup 포함)
        Collections.reverse(batches);
        List<CandleData> minutes = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (List<CandleData> batch : batches) {
            for (CandleData c : batch) {
                if (seen.add(c.getDate())) minutes.add(c);
            }
        }

        return aggregateToHourly(minutes);
    }

    /** 분봉 API (전일 포함 Y=Y), date 필드 = YYYYMMDDHHMMSS (14자) */
    private List<CandleData> fetchIntradayBatchWithDate(String stockCode, String hour) {
        throttleKisCall();
        String token = getRealToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", realAppKey);
        headers.set("appsecret", realAppSecret);
        headers.set("tr_id", "FHKST03010200");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = KIS_REAL_BASE
                + "/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice"
                + "?FID_ETC_CLS_CODE=0"
                + "&FID_INPUT_ISCD=" + stockCode
                + "&FID_INPUT_HOUR_1=" + hour
                + "&FID_COND_MRKT_DIV_CODE=J"
                + "&FID_PW_DATA_INCU_YN=Y";

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            JsonNode output2 = objectMapper.readTree(response.getBody()).path("output2");
            List<CandleData> result = new ArrayList<>();
            if (output2.isArray()) {
                for (JsonNode item : output2) {
                    String time = item.path("stck_cntg_hour").asText();
                    String date = item.path("stck_bsop_date").asText();
                    if (time.isEmpty() || time.equals("null") || date.isEmpty() || date.equals("null")) continue;
                    long vol = item.path("cntg_vol").asLong(0);
                    if (vol == 0) vol = item.path("acml_vol").asLong(0);
                    result.add(new CandleData(
                            date + time,                         // YYYYMMDDHHMMSS (14자)
                            item.path("stck_oprc").asLong(0),
                            item.path("stck_hgpr").asLong(0),
                            item.path("stck_lwpr").asLong(0),
                            item.path("stck_prpr").asLong(0),
                            vol
                    ));
                }
            }
            Collections.reverse(result); // KIS는 최신→과거 순, 역정렬
            return result;
        } catch (Exception e) {
            log.warn("시봉용 분봉 조회 실패 code={} hour={}: {}", stockCode, hour, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 분봉 목록 → 시봉으로 집계. key: YYYYMMDDHH (10자) */
    private List<CandleData> aggregateToHourly(List<CandleData> minutes) {
        String todayStr    = LocalDate.now().format(DATE_FMT);
        int    currentHour = LocalTime.now().getHour();

        LinkedHashMap<String, long[]> buckets = new LinkedHashMap<>();
        for (CandleData m : minutes) {
            String dt = m.getDate();
            if (dt.length() < 10) continue;
            String key = dt.substring(0, 10); // YYYYMMDDHH
            // 오늘 날짜인데 현재 시각보다 미래 시간대 봉이면 제외
            if (key.substring(0, 8).equals(todayStr)
                    && Integer.parseInt(key.substring(8, 10)) > currentHour) continue;
            long[] cur = buckets.get(key);
            if (cur == null) {
                buckets.put(key, new long[]{m.getOpen(), m.getHigh(), m.getLow(), m.getClose(), m.getVolume()});
            } else {
                cur[1] = Math.max(cur[1], m.getHigh());
                cur[2] = Math.min(cur[2], m.getLow());
                cur[3] = m.getClose(); // 마지막 분봉의 종가 = 시봉 종가
                cur[4] += m.getVolume();
            }
        }
        List<CandleData> result = new ArrayList<>();
        for (Map.Entry<String, long[]> e : buckets.entrySet()) {
            long[] v = e.getValue();
            result.add(new CandleData(e.getKey(), v[0], v[1], v[2], v[3], v[4]));
        }
        return result;
    }

    /**
     * 분봉 조회 (TR_ID: FHKST03010200).
     * 3 배치 × ~30개 = 최대 ~90분의 분봉을 오래된 순으로 반환.
     * CandleData.date 필드에 체결시간(HHMMSS) 저장.
     */
    public List<CandleData> fetchIntradayCandles(String stockCode) {
        List<CandleData> all = new ArrayList<>();
        String endTime = currentEndTime();

        for (int i = 0; i < 3; i++) {
            List<CandleData> batch = fetchIntradayBatch(stockCode, endTime);
            if (batch.isEmpty()) break;
            all.addAll(0, batch); // 앞에 삽입 → 오래된 순 유지
            endTime = subtractOneMinute(batch.get(0).getDate());
            if (i < 2) sleepQuietly(300);
        }
        // 장중 미래 봉 제거 (KIS가 endTime 이후 슬롯을 포함해 반환하는 경우 대비)
        String cutoff = currentEndTime();
        all.removeIf(c -> c.getDate().compareTo(cutoff) > 0);
        return all;
    }

    private List<CandleData> fetchIntradayBatch(String stockCode, String hour) {
        throttleKisCall();
        String token = getRealToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", realAppKey);
        headers.set("appsecret", realAppSecret);
        headers.set("tr_id", "FHKST03010200");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        String url = KIS_REAL_BASE
                + "/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice"
                + "?FID_ETC_CLS_CODE=0"
                + "&FID_INPUT_ISCD=" + stockCode
                + "&FID_INPUT_HOUR_1=" + hour
                + "&FID_COND_MRKT_DIV_CODE=J"
                + "&FID_PW_DATA_INCU_YN=N";

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            JsonNode output2 = objectMapper.readTree(response.getBody()).path("output2");
            List<CandleData> result = new ArrayList<>();
            if (output2.isArray()) {
                for (JsonNode item : output2) {
                    String time = item.path("stck_cntg_hour").asText();
                    if (time.isEmpty() || time.equals("null")) continue;
                    long vol = item.path("cntg_vol").asLong(0);
                    if (vol == 0) vol = item.path("acml_vol").asLong(0);
                    result.add(new CandleData(
                            time,
                            item.path("stck_oprc").asLong(0),
                            item.path("stck_hgpr").asLong(0),
                            item.path("stck_lwpr").asLong(0),
                            item.path("stck_prpr").asLong(0),
                            vol
                    ));
                }
            }
            Collections.reverse(result); // KIS는 최신→과거 순, 역정렬하여 오래된 순으로 변환
            return result;
        } catch (Exception e) {
            log.warn("분봉 조회 실패 code={} hour={}: {}", stockCode, hour, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** HHMMSS에서 1분을 뺀 시각 반환. 장 시작(09:00) 이전으로는 내려가지 않음. */
    private String subtractOneMinute(String hhmmss) {
        int totalMin = Integer.parseInt(hhmmss.substring(0, 2)) * 60
                     + Integer.parseInt(hhmmss.substring(2, 4)) - 1;
        if (totalMin < 540) return "090000"; // 09:00
        return String.format("%02d%02d00", totalMin / 60, totalMin % 60);
    }

    private void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");

    /**
     * 분봉 API 첫 배치의 종료 시각을 반환한다.
     * 장중(09:00~15:30)이면 현재 시각을 사용해 미래 봉이 포함되지 않게 한다.
     * 장 마감 후거나 장 시작 전이면 15:30(전 거래일 종료 시각)을 사용한다.
     */
    private String currentEndTime() {
        LocalTime now = LocalTime.now();
        LocalTime open  = LocalTime.of(9, 0);
        LocalTime close = LocalTime.of(15, 30);
        if (now.isAfter(open) && now.isBefore(close)) {
            return now.format(TIME_FMT);
        }
        return "153000";
    }

    /**
     * KIS 실전 도메인 모든 REST 호출 전에 반드시 통과해야 하는 전역 게이트 (EGW00201 방지).
     * synchronized로 직렬화 → 멀티스레드 환경에서 동시 호출이 불가능하다.
     */
    private synchronized void throttleKisCall() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastKisCallMs.get();
        if (elapsed < MIN_CALL_INTERVAL_MS) {
            sleepQuietly(MIN_CALL_INTERVAL_MS - elapsed);
        }
        lastKisCallMs.set(System.currentTimeMillis());
    }

    @Getter
    @Builder
    public static class RankItem {
        private String stockCode;
        private String stockName;
        private Long currentPrice;
        private Double changeRate;
        private Long tradeValue;
        private Long volume;
    }

    @Getter
    @AllArgsConstructor
    public static class CandleData {
        private String date;
        private long open;
        private long high;
        private long low;
        private long close;
        private long volume;
    }

    @Getter
    @AllArgsConstructor
    public static class IndexCandleData {
        private String date;
        private double open;
        private double high;
        private double low;
        private double close;
        private long volume;
    }

    @Getter
    @Builder
    public static class MarketIndexItem {
        private String code;
        private String name;
        private Double price;
        private Double change;
        private Double changeRate;
        private Long volume;
        private Long tradeValue;
    }

    @Getter
    @Builder
    public static class ExchangeRateItem {
        private String currency;
        private String name;
        private Double rate;
        private Double change;
        private Double changeRate;
    }
}
