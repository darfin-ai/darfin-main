package com.kosta.darfin.controller.fund;

import com.kosta.darfin.dto.fund.StockSummaryDTO;
import com.kosta.darfin.service.fund.StockRankService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 홈 화면 4개 탭(거래대금/거래량/급상승/급하락) 순위 조회.
 * KIS 실전 도메인의 volume-rank API 사용 (모의투자 미지원).
 * 조회 전용이라 실전 키를 써도 안전 — 매수/매도는 절대 이 컨트롤러에서 하지 않음.
 *
 * URL 규칙: 도메인 prefix(funds) + kebab-case 복수형
 */
@RestController
@RequestMapping("/funds/ranks")
@RequiredArgsConstructor
public class StockRankController {

    private final StockRankService stockRankService;

    /** GET /funds/ranks/trade-value — 거래대금 순위 */
    @GetMapping("/trade-value")
    public List<StockSummaryDTO> getTradeValueRank() {
        return stockRankService.getTradeValueRank();
    }

    /** GET /funds/ranks/volume — 거래량 순위 */
    @GetMapping("/volume")
    public List<StockSummaryDTO> getVolumeRank() {
        return stockRankService.getVolumeRank();
    }

    /** GET /funds/ranks/top-gainers — 급상승 순위 */
    @GetMapping("/top-gainers")
    public List<StockSummaryDTO> getTopGainers() {
        return stockRankService.getTopGainers();
    }

    /** GET /funds/ranks/top-losers — 급하락 순위 */
    @GetMapping("/top-losers")
    public List<StockSummaryDTO> getTopLosers() {
        return stockRankService.getTopLosers();
    }
}