package com.kosta.darfin.entity.analysis;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// Metrics.java — ddl.sql §7 metrics와 1:1 (darfin-company-analysis 파이프라인이 적재)
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

    @Column(nullable = false, length = 5)
    private String reprtCode;

    @Column(length = 300)
    private String concept;

    @Column(nullable = false, length = 200)
    private String accountNm;

    @Column(length = 20)
    private String statementType;

    @Column(nullable = false)
    private Boolean isConsolidated;

    @Column(length = 10)
    private String periodQualifier;

    private Long amount;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
