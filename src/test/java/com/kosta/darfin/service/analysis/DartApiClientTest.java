package com.kosta.darfin.service.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito-only unit tests (no Spring context) for {@link DartApiClient},
 * following the "unit-test the pure pieces by mocking RestTemplate" approach
 * described in track-F-dart-api-client.md.
 */
class DartApiClientTest {

    private RestTemplate restTemplate;
    private DartApiClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new DartApiClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "https://opendart.fss.or.kr/api");
    }

    private Map<String, Object> response(String status) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("message", "message for " + status);
        return data;
    }

    @Test
    void listFilings_status013_returnsEmptyList() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response("013"));

        List<Map<String, Object>> result = client.listFilings("00126380", "20240101", "20241231");

        assertThat(result).isEmpty();
        verify(restTemplate, times(1)).getForObject(anyString(), eq(Map.class));
    }

    @Test
    void reportApi_status013_returnsNull() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response("013"));

        List<Map<String, Object>> result = client.reportApi("hyslrSttus", "00126380", "2023", "11011");

        assertThat(result).isNull();
    }

    @Test
    void reportApi_status020_throwsQuotaExceededException() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response("020"));

        assertThatThrownBy(() -> client.reportApi("hyslrSttus", "00126380", "2023", "11011"))
                .isInstanceOf(DartApiClient.DartApiException.class)
                .satisfies(e -> {
                    DartApiClient.DartApiException ex = (DartApiClient.DartApiException) e;
                    assertThat(ex.status).isEqualTo("020");
                    assertThat(ex.isQuotaExceeded()).isTrue();
                });
    }

    @Test
    void reportApi_unknownStatus_throwsDartApiException() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response("900"));

        assertThatThrownBy(() -> client.reportApi("hyslrSttus", "00126380", "2023", "11011"))
                .isInstanceOf(DartApiClient.DartApiException.class)
                .satisfies(e -> {
                    DartApiClient.DartApiException ex = (DartApiClient.DartApiException) e;
                    assertThat(ex.status).isEqualTo("900");
                    assertThat(ex.isQuotaExceeded()).isFalse();
                });
    }

    @Test
    void reportApi_retriesOnRestClientException_thenSucceeds() {
        Map<String, Object> success = response("000");
        success.put("list", List.of(Map.of("k", "v")));

        // Fail twice, then succeed on the 3rd attempt (of 4 max).
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RestClientException("boom 1"))
                .thenThrow(new RestClientException("boom 2"))
                .thenReturn(success);

        List<Map<String, Object>> result = client.reportApi("hyslrSttus", "00126380", "2023", "11011");

        assertThat(result).hasSize(1);
        verify(restTemplate, times(3)).getForObject(anyString(), eq(Map.class));
    }

    @Test
    void reportApi_exhaustsAllRetries_thenThrowsLastError() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RestClientException("boom"));

        assertThatThrownBy(() -> client.reportApi("hyslrSttus", "00126380", "2023", "11011"))
                .isInstanceOf(RestClientException.class)
                .hasMessage("boom");

        verify(restTemplate, times(4)).getForObject(anyString(), eq(Map.class));
    }
}
