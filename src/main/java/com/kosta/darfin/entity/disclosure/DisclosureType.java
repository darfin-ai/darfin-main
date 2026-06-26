package com.kosta.darfin.entity.disclosure;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

// DisclosureType.java
@Entity
@Table(name = "disclosure_type")
@Getter
@NoArgsConstructor
public class DisclosureType {
    @Id
    @Column(length = 40)
    private String typeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_code", nullable = false)
    private DisclosureGroup disclosureGroup;

    @Column(nullable = false, length = 100)
    private String typeName;

    @Column(length = 2)
    private String pblntfTy;

    @Column(length = 4)
    private String pblntfDetailTy;

    @Column(length = 20)
    private String parsingStrategy;

    @Column(length = 10)
    private String urgencyTier;

    @Column(length = 20)
    private String bodyFormat;

    @Column(nullable = false, length = 20)
    private String riskScaleCode = "STANDARD";
}