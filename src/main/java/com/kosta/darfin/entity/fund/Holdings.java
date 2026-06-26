package com.kosta.darfin.entity.fund;

import com.kosta.darfin.entity.common.Users;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// Holdings.java
@Entity
@Table(name = "holdings")
@Getter
@NoArgsConstructor
public class Holdings {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long holdingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private StockInfo stockInfo;

    private Integer quantity;
    private Long avgBuyPrice;
    private LocalDateTime updatedAt;
}