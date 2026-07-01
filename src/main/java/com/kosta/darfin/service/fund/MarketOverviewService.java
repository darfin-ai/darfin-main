package com.kosta.darfin.service.fund;

import com.kosta.darfin.dto.fund.MarketMetricDTO;
import com.kosta.darfin.dto.fund.MarketOverviewDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketOverviewService {

    private final KisRankApiClient kisRankApiClient;

    public MarketOverviewDTO getMarketOverview() {
        MarketMetricDTO kospi = fetchIndexOrNull("0001", "KOSPI");
        MarketMetricDTO kosdaq = fetchIndexOrNull("1001", "KOSDAQ");
        MarketMetricDTO usdKrw = fetchExchangeOrNull();

        List<MarketMetricDTO> indices = new ArrayList<>();
        if (kospi != null) indices.add(kospi);
        if (kosdaq != null) indices.add(kosdaq);

        return new MarketOverviewDTO(indices, usdKrw);
    }

    private MarketMetricDTO fetchIndexOrNull(String indexCode, String indexName) {
        try {
            return toIndexDto(kisRankApiClient.fetchMarketIndex(indexCode, indexName));
        } catch (Exception e) {
            log.warn("{} 지수 조회 실패: {}", indexName, e.getMessage());
            return null;
        }
    }

    private MarketMetricDTO fetchExchangeOrNull() {
        try {
            return toExchangeDto(kisRankApiClient.fetchUsdKrwExchangeRate());
        } catch (Exception e) {
            log.warn("USD/KRW 환율 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    private MarketMetricDTO toIndexDto(KisRankApiClient.MarketIndexItem item) {
        return new MarketMetricDTO(
                item.getCode(),
                item.getName(),
                decimal(item.getPrice()),
                decimal(item.getChange()),
                decimal(item.getChangeRate()),
                item.getVolume(),
                item.getTradeValue()
        );
    }

    private MarketMetricDTO toExchangeDto(KisRankApiClient.ExchangeRateItem item) {
        return new MarketMetricDTO(
                item.getCurrency(),
                item.getName(),
                decimal(item.getRate()),
                decimal(item.getChange()),
                decimal(item.getChangeRate()),
                null,
                null
        );
    }

    private BigDecimal decimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : BigDecimal.ZERO;
    }
}
