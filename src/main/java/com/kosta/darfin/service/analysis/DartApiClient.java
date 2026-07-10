package com.kosta.darfin.service.analysis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synchronous DART Open API client. Java port of
 * darfin-company-analysis/dart_pipeline/async_dart_client.py's AsyncDartClient.
 *
 * This stays a simple blocking client - fanning out multiple api_id calls in
 * parallel is DartOverviewService's (Track H) job via a thread pool, not this
 * class's.
 */
@Slf4j
@Component
public class DartApiClient {

    @Value("${dart.api.key}")
    private String apiKey;

    @Value("${dart.api.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final AtomicLong lastCallNanos = new AtomicLong(0);
    private static final long THROTTLE_NANOS = 300_000_000L; // 0.3s

    public DartApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public static class DartApiException extends RuntimeException {
        public final String status;

        public DartApiException(String status, String message) {
            super(message);
            this.status = status;
        }

        public boolean isQuotaExceeded() {
            return "020".equals(status);
        }
    }

    private synchronized void throttle() {
        long now = System.nanoTime();
        long wait = THROTTLE_NANOS - (now - lastCallNanos.get());
        if (wait > 0) {
            try {
                Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallNanos.set(System.nanoTime());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listFilings(String corpCode, String bgnDe, String endDe) {
        List<Map<String, Object>> items = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            throttle();
            Map<String, String> params = new LinkedHashMap<>();
            params.put("crtfc_key", apiKey);
            params.put("corp_code", corpCode);
            params.put("bgn_de", bgnDe);
            params.put("end_de", endDe);
            params.put("pblntf_ty", "A");
            params.put("last_reprt_at", "Y");
            params.put("page_no", String.valueOf(pageNo));
            params.put("page_count", "100");

            Map<String, Object> data = getWithRetry("/list.json", params);
            String status = (String) data.get("status");
            if ("013".equals(status)) {
                return items;
            }
            if (!"000".equals(status)) {
                throw new DartApiException(status, String.valueOf(data.get("message")));
            }
            Object list = data.get("list");
            if (list instanceof List<?>) {
                items.addAll((List<Map<String, Object>>) list);
            }
            int totalPage = data.get("total_page") == null ? 1 : Integer.parseInt(String.valueOf(data.get("total_page")));
            if (pageNo >= totalPage) {
                return items;
            }
            pageNo++;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> reportApi(String apiId, String corpCode, String bsnsYear, String reprtCode) {
        throttle();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("crtfc_key", apiKey);
        params.put("corp_code", corpCode);
        params.put("bsns_year", bsnsYear);
        params.put("reprt_code", reprtCode);

        Map<String, Object> data = getWithRetry("/" + apiId + ".json", params);
        String status = (String) data.get("status");
        if ("013".equals(status)) {
            return null;
        }
        if (!"000".equals(status)) {
            throw new DartApiException(status, String.valueOf(data.get("message")));
        }
        Object list = data.get("list");
        if (list instanceof List<?>) {
            return (List<Map<String, Object>>) list;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getWithRetry(String path, Map<String, String> params) {
        StringBuilder url = new StringBuilder(baseUrl).append(path).append("?");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            url.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }

        RuntimeException lastError = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                Map<String, Object> result = restTemplate.getForObject(url.toString(), Map.class);
                if (result == null) {
                    throw new RestClientException("empty response body");
                }
                return result;
            } catch (RestClientException e) {
                lastError = e;
                log.warn("DART API call failed (attempt {}/4): {}", attempt + 1, e.getMessage());
                if (attempt < 3) {
                    try {
                        Thread.sleep((long) Math.pow(2, attempt) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw lastError;
                    }
                }
            }
        }
        throw lastError;
    }
}
