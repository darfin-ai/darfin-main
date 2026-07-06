package com.kosta.darfin.dto.fund;

import com.kosta.darfin.entity.fund.Funds;
import com.kosta.darfin.entity.fund.Holdings;
import com.kosta.darfin.entity.fund.Trades;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
        return from(funds, holdings, trades, java.util.Collections.emptyMap());
    }

    public static PortfolioResponse from(Funds funds,
                                         List<Holdings> holdings,
                                         List<Trades> trades,
                                         Map<String, Long> currentPrices) {
        return from(funds, holdings, trades, currentPrices, java.util.Collections.emptyMap());
    }

    public static PortfolioResponse from(Funds funds,
                                         List<Holdings> holdings,
                                         List<Trades> trades,
                                         Map<String, Long> currentPrices,
                                         Map<String, String> stockNames) {
        return new PortfolioResponse(
                new FundsInfo(funds.getInitialAmount(), funds.getCashBalance()),
                holdings.stream()
                        .map(h -> {
                            String code = h.getStockInfo().getStockCode();
                            return HoldingInfo.from(h, currentPrices.get(code), stockNames.get(code));
                        })
                        .collect(Collectors.toList()),
                trades.stream()
                        .map(t -> new TradeInfo(
                                t.getTradeId(),
                                t.getStockInfo().getStockCode(),
                                t.getType(),
                                t.getQuantity(),
                                t.getPrice(),
                                t.getTradedAt().toInstant(ZoneOffset.ofHours(9)).toEpochMilli(),
                                t.getRealizedPnl(),
                                t.getHoldDays()))
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
    public static class HoldingInfo {
        private String code;
        private String name;
        private String stockName;
        private Integer qty;
        private Long avgPrice;
        private Long currentPrice;
        private Long valuationPnl;
        private Double valuationPnlRate;

        public HoldingInfo(String code, Integer qty, Long avgPrice) {
            this(code, null, qty, avgPrice, null, null, null);
        }

        public HoldingInfo(String code,
                           String name,
                           Integer qty,
                           Long avgPrice,
                           Long currentPrice,
                           Long valuationPnl,
                           Double valuationPnlRate) {
            this.code = code;
            this.name = name;
            this.stockName = name;
            this.qty = qty;
            this.avgPrice = avgPrice;
            this.currentPrice = currentPrice;
            this.valuationPnl = valuationPnl;
            this.valuationPnlRate = valuationPnlRate;
        }

        private static HoldingInfo from(Holdings holding, Long currentPrice, String stockName) {
            String code = holding.getStockInfo().getStockCode();
            String name = stockName != null && !stockName.isBlank()
                    ? stockName
                    : holding.getStockInfo().getStockName();
            Integer qty = holding.getQuantity();
            Long avgPrice = holding.getAvgBuyPrice();
            if (currentPrice == null || qty == null || avgPrice == null) {
                return new HoldingInfo(code, name, qty, avgPrice, null, null, null);
            }

            long pnl = (currentPrice - avgPrice) * qty;
            double pnlRate = avgPrice > 0
                    ? (double) (currentPrice - avgPrice) / avgPrice * 100
                    : 0.0;

            return new HoldingInfo(
                    code,
                    name,
                    qty,
                    avgPrice,
                    currentPrice,
                    pnl,
                    Math.round(pnlRate * 100.0) / 100.0
            );
        }
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
        private Integer holdDays;
    }
}
