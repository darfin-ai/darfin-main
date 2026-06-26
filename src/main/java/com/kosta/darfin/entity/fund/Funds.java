package com.kosta.darfin.entity.fund;

import com.kosta.darfin.entity.common.Users;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Funds.java
@Entity
@Table(name = "funds")
@Getter
@NoArgsConstructor
public class Funds {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    private Long initialAmount;
    private Long cashBalance;
    private LocalDate startDate;
    private LocalDateTime updatedAt;
}