package com.kosta.darfin.entity.payment;

import com.kosta.darfin.entity.common.Users;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// Payments.java
// status: PENDING / DONE / FAILED / CANCELED
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

    @Column(nullable = false, length = 100)
    private String orderName;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 100, unique = true)
    private String merchantUid;

    @Column(length = 100)
    private String pgTid;

    @Column(length = 255)
    private String failReason;

    @Column(length = 500)
    private String receiptUrl;

    private LocalDateTime paidAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public Payments(Users user, PaymentMethods paymentMethod, String orderName, Integer amount,
                     String status, String merchantUid) {
        this.user = user;
        this.paymentMethod = paymentMethod;
        this.orderName = orderName;
        this.amount = amount;
        this.status = status;
        this.merchantUid = merchantUid;
    }

    public void markDone(String pgTid, String receiptUrl, LocalDateTime paidAt) {
        this.status = "DONE";
        this.pgTid = pgTid;
        this.receiptUrl = receiptUrl;
        this.paidAt = paidAt;
        this.failReason = null;
    }

    public void markFailed(String failReason) {
        this.status = "FAILED";
        this.failReason = failReason;
    }

    public void markCanceled() {
        this.status = "CANCELED";
    }
}
