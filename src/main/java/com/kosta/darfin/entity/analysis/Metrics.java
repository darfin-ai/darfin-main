package com.kosta.darfin.entity.analysis;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// Metrics.java
@Entity
@Table(name = "metrics")
@Getter
@NoArgsConstructor
public class Metrics {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rcept_no", nullable = false)
    private Filings filing;

    @Column(nullable = false, length = 8)
    private String corpCode;

    @Column(nullable = false, length = 4)
    private String bsnsYear;

    @Column(nullable = false, length = 200)
    private String accountNm;

    private Long amount;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}