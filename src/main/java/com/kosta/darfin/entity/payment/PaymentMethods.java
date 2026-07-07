package com.kosta.darfin.entity.payment;

import lombok.Builder;
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

    // 사용자가 등록 시 입력한 카드 별칭 (예: "내 신한카드")
    @Column(length = 50)
    private String cardName;

    @Column(nullable = false, length = 20)
    private String maskedCardNum;

    @Column(nullable = false)
    private Boolean isDefault = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public PaymentMethods(Users user, String billingKey, String cardCompany, String cardName,
                           String maskedCardNum, Boolean isDefault) {
        this.user = user;
        this.billingKey = billingKey;
        this.cardCompany = cardCompany;
        this.cardName = cardName;
        this.maskedCardNum = maskedCardNum;
        this.isDefault = isDefault != null && isDefault;
    }

    public void markDefault() {
        this.isDefault = true;
    }

    public void unmarkDefault() {
        this.isDefault = false;
    }
}
