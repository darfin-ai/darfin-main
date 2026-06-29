package com.kosta.darfin.entity.analysis;

import com.kosta.darfin.entity.common.Stock;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// Companies.java
@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor

public class Companies {
    @Id
    @Column (name = "corp_code",length = 8)
    private String corpCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corp_code", referencedColumnName = "dartCorpCode", insertable = false, updatable = false)
    private Stock stock;

    @Column(length = 100)
    private String sector;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}