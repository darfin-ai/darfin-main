package com.kosta.darfin.entity.fund;

import com.kosta.darfin.entity.common.Users;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// FundHistory.java
@Entity
@Table(name = "fund_history")
@Getter
@NoArgsConstructor
public class FundHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_trade_id")
    private Trades relatedTrade;

    @Column(nullable = false, length = 10)
    private String type;

    private Long amount;
    private LocalDateTime createdAt;
}