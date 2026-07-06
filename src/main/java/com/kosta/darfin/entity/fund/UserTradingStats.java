package com.kosta.darfin.entity.fund;

import com.kosta.darfin.entity.common.Users;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

// UserTradingStats.java
@Entity
@Table(name = "user_trading_stats")
@Getter
@Setter
@NoArgsConstructor
public class UserTradingStats {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long statId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private Users user;

    private Double monthlyTradeFreq;
    private Double avgHoldDays;
    private Double stopLossRate;
    private Double takeProfitRate;
    private Integer chaseBuyCount;
    private LocalDateTime updatedAt;
}