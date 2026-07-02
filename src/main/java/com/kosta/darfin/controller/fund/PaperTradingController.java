package com.kosta.darfin.controller.fund;

import com.kosta.darfin.dto.fund.PortfolioResponse;
import com.kosta.darfin.service.fund.PaperTradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/funds/paper")
@RequiredArgsConstructor
public class PaperTradingController {

    private final PaperTradingService paperTradingService;

    // =========================================================================
    // 포트폴리오 API — /funds/paper/**
    // =========================================================================

    @GetMapping("/portfolio")
    public PortfolioResponse getPortfolio(@AuthenticationPrincipal UserDetails user) {
        return paperTradingService.getPortfolio(user.getUsername());
    }

    @PostMapping("/buy")
    public PortfolioResponse buy(@AuthenticationPrincipal UserDetails user,
                                 @RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        int qty = ((Number) body.get("qty")).intValue();
        long price = ((Number) body.get("price")).longValue();
        return paperTradingService.buyPortfolio(user.getUsername(), code, qty, price);
    }

    @PostMapping("/sell")
    public PortfolioResponse sell(@AuthenticationPrincipal UserDetails user,
                                  @RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        int qty = ((Number) body.get("qty")).intValue();
        long price = ((Number) body.get("price")).longValue();
        return paperTradingService.sellPortfolio(user.getUsername(), code, qty, price);
    }

    @PostMapping("/charge")
    public PortfolioResponse charge(@AuthenticationPrincipal UserDetails user,
                                    @RequestBody Map<String, Object> body) {
        long amount = ((Number) body.get("amount")).longValue();
        return paperTradingService.charge(user.getUsername(), amount);
    }

    @PostMapping("/reset")
    public PortfolioResponse reset(@AuthenticationPrincipal UserDetails user) {
        return paperTradingService.reset(user.getUsername());
    }

    @PostMapping("/init-amount")
    public PortfolioResponse initAmount(@AuthenticationPrincipal UserDetails user,
                                        @RequestBody Map<String, Object> body) {
        long amount = ((Number) body.get("amount")).longValue();
        return paperTradingService.initAmount(user.getUsername(), amount);
    }
}
