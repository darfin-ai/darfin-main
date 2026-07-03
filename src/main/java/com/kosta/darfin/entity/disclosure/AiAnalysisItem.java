package com.kosta.darfin.entity.disclosure;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_analysis_item")
@Getter
@Setter
@NoArgsConstructor
public class AiAnalysisItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rcept_no", nullable = false)
    private Disclosure disclosure;

    @Column(nullable = false)
    private Byte itemNo;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 500)
    private String targetText;

    private Integer charStart;
    private Integer charEnd;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String materialImpact;

    @Column(nullable = false, length = 30)
    private String riskLabel;

    @Column(nullable = false)
    private Byte riskTier;

    @Column(columnDefinition = "json")
    private String extra;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}