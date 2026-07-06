package com.kosta.darfin.entity.analysis;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// TextChunks.java — ddl.sql §7 text_chunks와 1:1 (darfin-company-analysis 파이프라인이 적재)
@Entity
@Table(name = "text_chunks")
@Getter
@NoArgsConstructor
public class TextChunks {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rcept_no", nullable = false)
    private Filings filing;

    @Column(nullable = false, length = 8)
    private String corpCode;

    @Column(nullable = false, length = 200)
    private String sectionTitle;

    @Column(length = 30)
    private String canonicalLabel;

    @Column(length = 30)
    private String assocNote;

    @Column(length = 10)
    private String atocid;

    @Column(nullable = false, length = 500)
    private String breadcrumb;

    @Column(nullable = false)
    private Integer sectionLevel;

    @Column(nullable = false)
    private Integer sectionOrder;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(columnDefinition = "JSON")
    private String tablesJson;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false)
    private Integer chunkIndex;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
