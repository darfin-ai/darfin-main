package com.kosta.darfin.controller.fund;

import com.kosta.darfin.dto.fund.MarketOverviewDTO;
import com.kosta.darfin.service.fund.MarketOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/funds/market-overview")
@RequiredArgsConstructor
public class MarketOverviewController {

    private final MarketOverviewService marketOverviewService;

    @GetMapping
    public MarketOverviewDTO getMarketOverview() {
        return marketOverviewService.getMarketOverview();
    }
}
