package com.kosta.darfin.entity.disclosure;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "risk_scale")
@IdClass(RiskScaleId.class)
@Getter
@NoArgsConstructor
public class RiskScale {
    @Id
    @Column(length = 20)
    private String riskScaleCode;

    @Id
    @Column(length = 30)
    private String riskLabel;

    @Column(nullable = false)
    private Byte riskTier;

    @Column(nullable = false)
    private Byte displayOrder;
}