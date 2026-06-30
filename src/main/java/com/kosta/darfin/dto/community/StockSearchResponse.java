package com.kosta.darfin.dto.community;

import com.kosta.darfin.entity.common.Stock;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockSearchResponse {
    private Long id;
    private String dartCorpCode;
    private String companyName;
    private String stockCode;
    private String marketType;

    public static StockSearchResponse from(Stock stock) {
        return StockSearchResponse.builder()
                .id(stock.getId())
                .dartCorpCode(stock.getDartCorpCode())
                .companyName(stock.getCompanyName())
                .stockCode(stock.getStockCode())
                .marketType(stock.getMarketType())
                .build();
    }
}
