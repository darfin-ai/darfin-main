package com.kosta.darfin.controller.fund;

import com.kosta.darfin.dto.fund.PortfolioResponse;
import com.kosta.darfin.service.fund.PaperTradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 모의투자 포트폴리오 — 사용자별 DB 영속.
 * 모든 엔드포인트는 JWT 인증 필요 (SecurityConfig에서 /funds/paper/** authenticated 설정).
 */
@RestController
@RequestMapping("/funds/paper")
@RequiredArgsConstructor
public class PaperTradingController {

    private final PaperTradingService paperTradingService;

    /** 로그인 직후 전체 포트폴리오 로드 */
    @GetMapping("/portfolio")
    public PortfolioResponse getPortfolio(@AuthenticationPrincipal UserDetails user) {
        return paperTradingService.getPortfolio(user.getUsername());
    }

    /** 매수 — body: { code, qty, price } */
    @PostMapping("/buy")
    public PortfolioResponse buy(@AuthenticationPrincipal UserDetails user,
                                 @RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        int qty = ((Number) body.get("qty")).intValue();
        long price = ((Number) body.get("price")).longValue();
        return paperTradingService.buy(user.getUsername(), code, qty, price);
    }

    /** 매도 — body: { code, qty, price } */
    @PostMapping("/sell")
    public PortfolioResponse sell(@AuthenticationPrincipal UserDetails user,
                                  @RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        int qty = ((Number) body.get("qty")).intValue();
        long price = ((Number) body.get("price")).longValue();
        return paperTradingService.sell(user.getUsername(), code, qty, price);
    }

    /** 투자금 충전 — body: { amount } */
    @PostMapping("/charge")
    public PortfolioResponse charge(@AuthenticationPrincipal UserDetails user,
                                    @RequestBody Map<String, Object> body) {
        long amount = ((Number) body.get("amount")).longValue();
        return paperTradingService.charge(user.getUsername(), amount);
    }

    /** 초기화 — 보유종목 전부 삭제 + 잔고 복원 */
    @PostMapping("/reset")
    public PortfolioResponse reset(@AuthenticationPrincipal UserDetails user) {
        return paperTradingService.reset(user.getUsername());
    }

    /** 초기 투자금 설정 — body: { amount } */
    @PostMapping("/init-amount")
    public PortfolioResponse initAmount(@AuthenticationPrincipal UserDetails user,
                                        @RequestBody Map<String, Object> body) {
        long amount = ((Number) body.get("amount")).longValue();
        return paperTradingService.initAmount(user.getUsername(), amount);
    }
}
