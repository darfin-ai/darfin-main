package com.kosta.darfin.entity.analysis;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// LlmSummaries.java — ddl.sql §7 llm_summaries와 1:1 (darfin-company-analysis 파이프라인이 적재)
@Entity
@Table(name = "llm_summaries")
@Getter
@NoArgsConstructor
public class LlmSummaries {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rcept_no", nullable = false)
    private Filings filing;

    @Column(nullable = false, length = 8)
    private String corpCode;

    @Column(nullable = false, length = 50)
    private String summaryType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "JSON")
    private String sourceRefs;

    @Column(nullable = false, length = 100)
    private String modelUsed;

    private Integer tokensIn;

    private Integer tokensOut;

    @Column(precision = 10, scale = 6)
    private java.math.BigDecimal costUsd;

    private Integer latencyMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
