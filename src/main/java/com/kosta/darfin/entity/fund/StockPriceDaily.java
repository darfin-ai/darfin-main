package com.kosta.darfin.entity.fund;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

// StockPriceDaily.java
@Entity
@Table(name = "stock_price_daily")
@Getter
@NoArgsConstructor
public class StockPriceDaily {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dailyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private StockInfo stockInfo;

    @Column(nullable = false)
    private LocalDate tradeDate;

    private Long openPrice;
    private Long highPrice;
    private Long lowPrice;
    private Long closePrice;
    private Long volume;
    private LocalDateTime createdAt;
}