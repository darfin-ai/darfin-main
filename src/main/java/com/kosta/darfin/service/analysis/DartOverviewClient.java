package com.kosta.darfin.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Proxies dartOverview read-through to darfin-company-analysis FastAPI (:8003).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DartOverviewClient {

    @Value("${company.analysis.service.base-url:http://127.0.0.1:8003}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @return DartOverview map, or null if service unavailable / error
     */
    public Object fetchDartOverview(String corpCode) {
        String url = baseUrl + "/dart/overview/" + corpCode;
        try {
            String json = restTemplate.getForObject(url, String.class);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, Object.class);
        } catch (RestClientException e) {
            log.warn("dartOverview fetch failed for {}: {}", corpCode, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("dartOverview parse failed for {}: {}", corpCode, e.getMessage());
            return null;
        }
    }
}
