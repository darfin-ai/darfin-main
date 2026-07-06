package com.kosta.darfin.entity.fund;

import com.kosta.darfin.entity.common.Users;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "funds")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Funds {
    public static final long DEFAULT_INITIAL_AMOUNT = 10_000_000L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    private Long initialAmount;
    private Long cashBalance;
    private LocalDate startDate;
    private LocalDateTime updatedAt;

    public void updateCashBalance(long cashBalance) {
        this.cashBalance = cashBalance;
        this.updatedAt = LocalDateTime.now();
    }

    public void initAmount(long amount) {
        this.initialAmount = amount;
        this.cashBalance = amount;
        this.startDate = LocalDate.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void deductBalance(long amount) {
        this.cashBalance -= amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void addBalance(long amount) {
        this.cashBalance += amount;
        this.updatedAt = LocalDateTime.now();
    }
}
