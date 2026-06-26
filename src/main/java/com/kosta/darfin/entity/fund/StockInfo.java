package com.kosta.darfin.entity.fund;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// StockInfo.java
@Entity
@Table(name = "stock_info")
@Getter
@NoArgsConstructor
public class StockInfo {
    @Id
    @Column(length = 20)
    private String stockCode;

    @Column(nullable = false, length = 100)
    private String stockName;

    @Column(length = 20)
    private String market;

    @Column(length = 50)
    private String sector;

    private Long marketCap;
    private Double per;
    private Double pbr;
    private Double dividendYield;
    private LocalDateTime updatedAt;
}