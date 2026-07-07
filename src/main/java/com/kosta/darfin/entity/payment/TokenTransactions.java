package com.kosta.darfin.entity.payment;

import com.kosta.darfin.entity.common.Users;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// TokenTransactions.java
// type: CHARGE(차감) / RESET(정기 초기화) / REFUND(환불 복원)
// featureType: DISCLOSURE / COMPANY / PORTFOLIO_REPORT / RESET
@Entity
@Table(name = "token_transactions")
@Getter
@NoArgsConstructor
public class TokenTransactions {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 30)
    private String featureType;

    @Column(length = 100)
    private String resourceId;

    // 차감은 음수, 초기화/환불은 양수
    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false)
    private Integer balanceAfter;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public TokenTransactions(Users user, String type, String featureType, String resourceId,
                              Integer amount, Integer balanceAfter) {
        this.user = user;
        this.type = type;
        this.featureType = featureType;
        this.resourceId = resourceId;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }
}
