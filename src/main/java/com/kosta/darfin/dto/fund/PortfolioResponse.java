package com.kosta.darfin.dto.fund;

import com.kosta.darfin.entity.fund.Funds;
import com.kosta.darfin.entity.fund.Holdings;
import com.kosta.darfin.entity.fund.Trades;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class PortfolioResponse {

    private FundsInfo funds;
    private List<HoldingInfo> holdings;
    private List<TradeInfo> trades;

    public static PortfolioResponse from(Funds funds,
                                         List<Holdings> holdings,
                                         List<Trades> trades) {
        return new PortfolioResponse(
                new FundsInfo(funds.getInitialAmount(), funds.getCashBalance()),
                holdings.stream()
                        .map(h -> new HoldingInfo(
                                h.getStockInfo().getStockCode(),
                                h.getQuantity(),
                                h.getAvgBuyPrice()))
                        .collect(Collectors.toList()),
                trades.stream()
                        .map(t -> new TradeInfo(
                                t.getTradeId(),
                                t.getStockInfo().getStockCode(),
                                t.getType(),
                                t.getQuantity(),
                                t.getPrice(),
                                t.getTradedAt().toInstant(ZoneOffset.ofHours(9)).toEpochMilli(),
                                t.getRealizedPnl()))
                        .collect(Collectors.toList())
        );
    }

    @Getter
    @AllArgsConstructor
    public static class FundsInfo {
        private Long initialAmount;
        private Long cashBalance;
    }

    @Getter
    @AllArgsConstructor
    public static class HoldingInfo {
        private String code;
        private Integer qty;
        private Long avgPrice;
    }

    @Getter
    @AllArgsConstructor
    public static class TradeInfo {
        private Long id;
        private String code;
        private String type;
        private Integer qty;
        private Long price;
        private Long ts;
        private Long pnl;
    }
}
