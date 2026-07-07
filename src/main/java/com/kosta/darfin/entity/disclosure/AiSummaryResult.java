package com.kosta.darfin.entity.disclosure;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_summary_result")
@Getter
@Setter
@NoArgsConstructor
public class AiSummaryResult {

    @Id
    @Column(length = 14)
    private String rceptNo;

    @Column(nullable = false, length = 200)
    private String summaryText;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String investorComment;

    @Column(nullable = false, length = 30)
    private String riskLabel;

    @Column(nullable = false)
    private Byte riskTier;

    @Column(columnDefinition = "json")
    private String extra;

    private Integer compressedContextChars;

    @Column(nullable = false, length = 40)
    private String modelName;

    private Integer tokensIn;

    private Integer tokensOut;

    @Column(precision = 10, scale = 6)
    private BigDecimal costUsd;

    private Integer latencyMs;

    @Column(nullable = false)
    private Boolean cacheHit = false;

    @Column(length = 40)
    private String errorCode;

    @Column(length = 300)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
