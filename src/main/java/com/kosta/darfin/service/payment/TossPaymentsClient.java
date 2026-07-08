package com.kosta.darfin.service.payment;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 자동결제(빌링) REST API 클라이언트.
 * 응답 필드명(card.company 등)은 토스 개발자센터 문서 기준이며, 실제 연동 테스트 시
 * 발급받은 시크릿 키로 한 번 호출해 실제 응답 구조를 확인 후 필요시 파싱 로직을 보정한다.
 */
@Slf4j
@Component
public class TossPaymentsClient {

    private final RestTemplate restTemplate;
    private final String secretKey;
    private final String baseUrl;

    public TossPaymentsClient(RestTemplate restTemplate,
                               @Value("${toss.payments.secret-key:}") String secretKey,
                               @Value("${toss.payments.base-url:https://api.tosspayments.com/v1}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.secretKey = secretKey;
        this.baseUrl = removeTrailingSlash(baseUrl);
    }

    // authKey(프론트 tossPayments.requestBillingAuth 성공 후 콜백으로 받은 값) → billingKey 발급
    public BillingKeyResult issueBillingKey(String authKey, String customerKey) {
        Map<String, Object> body = Map.of("authKey", authKey, "customerKey", customerKey);
        Map<String, Object> res = post(baseUrl + "/billing/authorizations/issue", body);

        String billingKey = (String) res.get("billingKey");
        Map<String, Object> card = asMap(res.get("card"));
        String cardCompany = card != null ? String.valueOf(card.get("company")) : "UNKNOWN";
        String maskedCardNumber = card != null ? String.valueOf(card.get("number")) : "";

        return new BillingKeyResult(billingKey, cardCompany, maskedCardNumber);
    }

    // 저장된 billingKey로 정기결제 청구
    public ChargeResult chargeBilling(String billingKey, String customerKey, int amount,
                                       String orderId, String orderName) {
        Map<String, Object> body = Map.of(
                "customerKey", customerKey,
                "amount", amount,
                "orderId", orderId,
                "orderName", orderName
        );
        Map<String, Object> res = post(baseUrl + "/billing/" + billingKey, body);
        Map<String, Object> receipt = asMap(res.get("receipt"));
        String receiptUrl = receipt != null ? (String) receipt.get("url") : null;
        return new ChargeResult((String) res.get("paymentKey"), (String) res.get("status"), receiptUrl);
    }

    // 결제 취소(전액/부분 환불). cancelAmount가 null이면 전액 취소
    public void cancelPayment(String paymentKey, String cancelReason, Integer cancelAmount) {
        Map<String, Object> body = cancelAmount != null
                ? Map.of("cancelReason", cancelReason, "cancelAmount", cancelAmount)
                : Map.of("cancelReason", cancelReason);
        post(baseUrl + "/payments/" + paymentKey + "/cancel", body);
    }

    private Map<String, Object> post(String url, Map<String, Object> body) {
        validateConfigured();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey());

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.warn("[Toss 결제 거절] url={} status={} body={}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "결제가 거절되었습니다. 카드 정보를 확인해주세요.");
        } catch (HttpServerErrorException e) {
            log.error("[Toss API 서버 오류] url={} status={}", url, e.getStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "결제 서비스와 통신에 실패했습니다.");
        }
    }

    private String encodedSecretKey() {
        return Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    private void validateConfigured() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "결제 서비스 설정이 필요합니다. toss.payments.secret-key를 설정해주세요.");
        }
    }

    private String removeTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.tosspayments.com/v1";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        return obj instanceof Map ? (Map<String, Object>) obj : null;
    }

    @Getter
    public static class BillingKeyResult {
        private final String billingKey;
        private final String cardCompany;
        private final String maskedCardNumber;

        public BillingKeyResult(String billingKey, String cardCompany, String maskedCardNumber) {
            this.billingKey = billingKey;
            this.cardCompany = cardCompany;
            this.maskedCardNumber = maskedCardNumber;
        }
    }

    @Getter
    public static class ChargeResult {
        private final String paymentKey;
        private final String status;
        private final String receiptUrl;

        public ChargeResult(String paymentKey, String status, String receiptUrl) {
            this.paymentKey = paymentKey;
            this.status = status;
            this.receiptUrl = receiptUrl;
        }
    }
}
