package com.kosta.darfin.entity.disclosure;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@EqualsAndHashCode // 핵심: 이 어노테이션 하나로 equals와 hashCode가 자동 생성됩니다.
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드를 포함하는 생성자
public class RiskScaleId implements Serializable {
    private String riskScaleCode;
    private String riskLabel;
}