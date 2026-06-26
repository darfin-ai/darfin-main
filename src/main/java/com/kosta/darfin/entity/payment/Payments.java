package com.kosta.darfin.entity.payment;

import com.kosta.darfin.entity.common.Users;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// Payments.java
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
public class Payments {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethods paymentMethod;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 100, unique = true)
    private String merchantUid;

    @Column(length = 100)
    private String pgTid;

    private LocalDateTime paidAt;
}