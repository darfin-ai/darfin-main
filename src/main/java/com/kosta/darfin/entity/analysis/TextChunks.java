package com.kosta.darfin.entity.analysis;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// TextChunks.java
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
    private String sectionNm;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Integer chunkIndex;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}