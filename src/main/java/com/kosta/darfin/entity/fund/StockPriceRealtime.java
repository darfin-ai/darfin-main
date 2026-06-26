package com.kosta.darfin.entity.fund;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// StockPriceRealtime.java
@Entity
@Table(name = "stock_price_realtime")
@Getter
@NoArgsConstructor
public class StockPriceRealtime {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long priceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private StockInfo stockInfo;

    private Long currentPrice;
    private Long prevClosePrice;

    @Column(precision = 10, scale = 4)
    private BigDecimal changeRate;

    private Long volume;
    private Long high52w;
    private Long low52w;
    private LocalDateTime fetchedAt;
}