package com.kosta.darfin.entity.fund;

import com.kosta.darfin.entity.common.Users;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// Trades.java
@Entity
@Table(name = "trades")
@Getter
@NoArgsConstructor
public class Trades {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tradeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private StockInfo stockInfo;

    @Column(length = 10)
    private String type;

    @Column(length = 10)
    private String status;

    @Column(length = 50)
    private String kisOrderNo;

    private Integer quantity;
    private Long price;
    private Long realizedPnl;
    private Integer holdDays;
    private LocalDateTime tradedAt;
    private LocalDateTime deletedAt;
}