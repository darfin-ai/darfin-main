package com.kosta.darfin.controller.fund;

import com.kosta.darfin.dto.fund.BalanceResponse;
import com.kosta.darfin.dto.fund.HoldingResponse;
import com.kosta.darfin.dto.fund.OrderRequest;
import com.kosta.darfin.dto.fund.OrderResponse;
import com.kosta.darfin.dto.fund.PortfolioResponse;
import com.kosta.darfin.service.fund.PaperTradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
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

    // =========================================================================
    // 주문 API — /funds/paper-trading/** (절대 경로)
    // =========================================================================

    @GetMapping("/funds/paper-trading/balance")
    public BalanceResponse getBalance(@AuthenticationPrincipal UserDetails userDetails) {
        return paperTradingService.getBalance(userDetails.getUsername());
    }

    @GetMapping("/funds/paper-trading/holdings/{stockCode}")
    public HoldingResponse getHolding(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String stockCode) {
        return paperTradingService.getHolding(userDetails.getUsername(), stockCode);
    }

    @PostMapping("/funds/paper-trading/orders/buy")
    public ResponseEntity<OrderResponse> orderBuy(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid OrderRequest request) {
        return ResponseEntity.ok(paperTradingService.buy(userDetails.getUsername(), request));
    }

    @PostMapping("/funds/paper-trading/orders/sell")
    public ResponseEntity<OrderResponse> orderSell(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid OrderRequest request) {
        return ResponseEntity.ok(paperTradingService.sell(userDetails.getUsername(), request));
    }
}
