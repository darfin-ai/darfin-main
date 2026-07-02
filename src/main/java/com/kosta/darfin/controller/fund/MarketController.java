package com.kosta.darfin.controller.fund;

import com.kosta.darfin.dto.fund.MarketMetricDTO;
import com.kosta.darfin.dto.fund.MarketOverviewDTO;
import com.kosta.darfin.service.fund.KisRankApiClient;
import com.kosta.darfin.service.fund.MarketOverviewService;
import com.kosta.darfin.service.fund.StockRankService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/funds/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketOverviewService marketOverviewService;
    private final KisRankApiClient kisRankApiClient;
    private final StockRankService stockRankService;

    @GetMapping("/indices")
    public Map<String, MarketMetricDTO> getMarketIndices() {
        MarketOverviewDTO overview = marketOverviewService.getMarketOverview();
        Map<String, MarketMetricDTO> result = new LinkedHashMap<>();
        for (MarketMetricDTO index : overview.getIndices()) {
            if ("0001".equals(index.getCode())) {
                result.put("kospi", index);
            } else if ("1001".equals(index.getCode())) {
                result.put("kosdaq", index);
            }
        }
        result.put("usd", overview.getUsdKrw());
        return result;
    }

    /** 지수 일봉 차트. indexCode: KOSPI=0001, KOSDAQ=1001 */
    @GetMapping("/indices/{indexCode}/candles")
    public List<KisRankApiClient.IndexCandleData> getIndexCandles(@PathVariable String indexCode) {
        return kisRankApiClient.fetchIndexDailyCandles(indexCode);
    }

    /** USD/KRW 환율 일봉 차트 */
    @GetMapping("/exchange/usd-krw/candles")
    public List<KisRankApiClient.IndexCandleData> getUsdKrwCandles() {
        return kisRankApiClient.fetchExchangeRateDailyCandles();
    }

    /** 지수 당일 분봉 (오늘 장중 흐름). indexCode: KOSPI=0001, KOSDAQ=1001 */
    @GetMapping("/indices/{indexCode}/candles/intraday")
    public List<KisRankApiClient.IndexCandleData> getIndexIntradayCandles(@PathVariable String indexCode) {
        return kisRankApiClient.fetchIndexIntradayCandles(indexCode);
    }

    /**
     * 지금 뜨는 산업 — 코스피 업종별(반도체/화학/건설 등, KIS FHPUP02140000) 등락률 순위.
     * 응답: [{ name, code, pct }, ...]  등락률 내림차순 최대 10개.
     */
    @GetMapping("/industries")
    public List<Map<String, Object>> getIndustries() {
        return stockRankService.getIndustrySectorRanks();
    }

    /**
     * 국내 투자자 동향 — 코스피 전체 개인/외국인/기관 순매수 거래대금(KIS FHPTJ04040000, 당일).
     * 응답: [{ who:"개인"|"외국인"|"기관", val(백만원), buy(boolean) }, ...]
     */
    @GetMapping("/investor-sentiment")
    public List<Map<String, Object>> getInvestorSentiment() {
        return stockRankService.getMarketInvestorSentiment();
    }
}

