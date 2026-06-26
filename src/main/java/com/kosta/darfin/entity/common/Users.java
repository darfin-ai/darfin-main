package com.kosta.darfin.entity.common;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// Users.java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, length = 255)
    private String phone;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 255)
    private String profileImage;

    @Column(nullable = false, length = 20)
    private String provider = "LOCAL";

    @Column(length = 100)
    private String providerUserId;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(nullable = false, length = 20)
    private String subscriptionLevel = "FREE";

    @Column(nullable = false)
    private Integer tokenBalance = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}