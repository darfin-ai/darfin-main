package com.kosta.darfin.entity.disclosure;

import com.kosta.darfin.entity.common.Stock;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "disclosure")
@Getter
@Setter
@NoArgsConstructor
public class Disclosure {
    @Id
    @Column(length = 14)
    private String rceptNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dart_corp_code", referencedColumnName = "dartCorpCode", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_code", nullable = false)
    private DisclosureType disclosureType;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 100)
    private String filerName;

    @Column(nullable = false)
    private LocalDate filedAt;

    @Column(length = 300)
    private String rawZipPath;

    @Column(length = 300)
    private String rawTextPath;

    @Column(columnDefinition = "json")
    private String missingTargets;

    @Column(nullable = false)
    private Short droppedCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}