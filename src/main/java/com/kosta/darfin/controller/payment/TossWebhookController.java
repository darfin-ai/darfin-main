package com.kosta.darfin.controller.payment;

import com.kosta.darfin.service.payment.TossWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 토스페이먼츠 결제 상태 변경 웹훅 수신. JWT 없이 토스 서버가 직접 호출하므로
 * SecurityConfig에서 permitAll 처리한다. 실 연동 시 토스 개발자센터에 등록한
 * 웹훅 시크릿으로 요청 서명을 검증하는 로직을 추가해야 한다(현재는 수신/반영만 처리).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/toss")
@RequiredArgsConstructor
public class TossWebhookController {

    private final TossWebhookService tossWebhookService;

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody Map<String, Object> payload) {
        log.info("[TossWebhook] 수신: {}", payload);
        tossWebhookService.handle(payload);
        return ResponseEntity.ok().build();
    }
}
