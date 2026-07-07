package com.kosta.darfin.service.payment;

public enum FeatureType {
    DISCLOSURE,       // 공시 요약/분석 (rceptNo 단위 열람권)
    COMPANY,          // 기업분석 (corpCode 단위 열람권)
    PORTFOLIO_REPORT, // 투자분석 리포트 생성 (매번 차감)
    RESET             // 정기 초기화/플랜 변경 반영
}
