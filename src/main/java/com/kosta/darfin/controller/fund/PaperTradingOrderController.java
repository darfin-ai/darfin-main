package com.kosta.darfin.controller.fund;

import com.kosta.darfin.dto.fund.BalanceResponse;
import com.kosta.darfin.dto.fund.HoldingResponse;
import com.kosta.darfin.dto.fund.OrderRequest;
import com.kosta.darfin.dto.fund.OrderResponse;
import com.kosta.darfin.service.fund.PaperTradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

// =========================================================================
// 주문 API — /funds/paper-trading/**
// =========================================================================
@RestController
@RequestMapping("/funds/paper-trading")
@RequiredArgsConstructor
public class PaperTradingOrderController {

    private final PaperTradingService paperTradingService;

    @GetMapping("/balance")
    public BalanceResponse getBalance(@AuthenticationPrincipal UserDetails userDetails) {
        return paperTradingService.getBalance(userDetails.getUsername());
    }

    @GetMapping("/holdings/{stockCode}")
    public HoldingResponse getHolding(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String stockCode) {
        return paperTradingService.getHolding(userDetails.getUsername(), stockCode);
    }

    @PostMapping("/orders/buy")
    public ResponseEntity<OrderResponse> orderBuy(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid OrderRequest request) {
        return ResponseEntity.ok(paperTradingService.buy(userDetails.getUsername(), request));
    }

    @PostMapping("/orders/sell")
    public ResponseEntity<OrderResponse> orderSell(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid OrderRequest request) {
        return ResponseEntity.ok(paperTradingService.sell(userDetails.getUsername(), request));
    }
}
