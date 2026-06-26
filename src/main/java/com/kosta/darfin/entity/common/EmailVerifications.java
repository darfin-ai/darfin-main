package com.kosta.darfin.entity.common;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// EmailVerifications.java
@Entity
@Table(name = "email_verifications")
@Getter
@NoArgsConstructor
public class EmailVerifications {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false, length = 20)
    private String purpose;

    @Column(nullable = false)
    private Boolean isVerified = false;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}