package com.kosta.darfin.entity.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

import com.kosta.darfin.entity.common.Users;

import java.time.LocalDateTime;

// PaymentMethods.java
@Entity
@Table(name = "payment_methods")
@Getter
@NoArgsConstructor
public class PaymentMethods {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(nullable = false, length = 255)
    private String billingKey;

    @Column(nullable = false, length = 50)
    private String cardCompany;

    @Column(nullable = false, length = 20)
    private String maskedCardNum;

    @Column(nullable = false)
    private Boolean isDefault = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}