package com.kosta.darfin.entity.common;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

// Stock.java
@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor
public class Stock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String marketType;

    @Column(nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false, length = 20, unique = true)
    private String dartCorpCode;

    @Column(length = 20, unique = true)
    private String stockCode;
}