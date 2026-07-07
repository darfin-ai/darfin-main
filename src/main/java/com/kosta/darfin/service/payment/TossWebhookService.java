package com.kosta.darfin.service.payment;

import com.kosta.darfin.entity.payment.Payments;
import com.kosta.darfin.repository.payment.PaymentsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 토스페이먼츠 결제 상태 변경 웹훅 처리. payload 구조는 토스 개발자센터 문서 기준
 * ({"eventType":"PAYMENT_STATUS_CHANGED","data":{"orderId","paymentKey","status",...}})이며,
 * 실제 연동 시 받아본 페이로드로 필드명을 재검증한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossWebhookService {

    private final PaymentsRepository paymentsRepository;

    @Transactional
    public void handle(Map<String, Object> payload) {
        Object dataObj = payload.get("data");
        if (!(dataObj instanceof Map)) {
            log.warn("[TossWebhook] data 필드가 없는 페이로드: {}", payload);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataObj;
        String orderId = (String) data.get("orderId");
        String status = (String) data.get("status");
        if (orderId == null || status == null) {
            log.warn("[TossWebhook] orderId/status 누락: {}", data);
            return;
        }

        Optional<Payments> paymentOpt = paymentsRepository.findByMerchantUid(orderId);
        if (paymentOpt.isEmpty()) {
            log.warn("[TossWebhook] 일치하는 결제 없음 orderId={}", orderId);
            return;
        }

        Payments payment = paymentOpt.get();
        switch (status) {
            case "DONE":
                if (!"DONE".equals(payment.getStatus())) {
                    payment.markDone((String) data.get("paymentKey"), payment.getReceiptUrl(), LocalDateTime.now());
                }
                break;
            case "CANCELED":
            case "PARTIAL_CANCELED":
                payment.markCanceled();
                break;
            default:
                log.info("[TossWebhook] 처리 대상 아닌 status={} orderId={}", status, orderId);
        }
    }
}
