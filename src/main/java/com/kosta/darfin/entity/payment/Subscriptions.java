package com.kosta.darfin.entity.payment;

import com.kosta.darfin.entity.common.Users;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Subscriptions.java
@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor
public class Subscriptions {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private Users user;

    @Column(nullable = false, length = 20)
    private String planName;

    @Column(nullable = false, length = 20)
    private String status;

    private LocalDate nextPaymentDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}