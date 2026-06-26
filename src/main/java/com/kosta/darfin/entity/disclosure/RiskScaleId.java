package com.kosta.darfin.entity.disclosure;

import javax.persistence.*;
import java.io.Serializable;

// RiskScaleId.java (복합키 처리용 클래스)
public class RiskScaleId implements Serializable {
    private String riskScaleCode;
    private String riskLabel;
    // equals()와 hashCode() 구현 필요
}