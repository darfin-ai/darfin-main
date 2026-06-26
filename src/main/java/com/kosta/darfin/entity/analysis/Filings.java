package com.kosta.darfin.entity.analysis;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// Filings.java
@Entity
@Table(name = "filings")
@Getter
@NoArgsConstructor
public class Filings {
    @Id
    @Column(length = 14)
    private String rceptNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corp_code", nullable = false)
    private Companies company;

    @Column(nullable = false, length = 100)
    private String corpName;

    @Column(nullable = false, length = 4)
    private String bsnsYear;

    @Column(nullable = false, length = 5)
    private String reprtCode;

    @Column(nullable = false, length = 8)
    private String filedDate;

    @Column(length = 300)
    private String xmlPath;

    @Column(nullable = false)
    private String pipelineStatus = "RAW"; // DB Enum 처리

    @Column(nullable = false)
    private LocalDateTime createdAt;
}