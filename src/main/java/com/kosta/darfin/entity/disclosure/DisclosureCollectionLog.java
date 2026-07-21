package com.kosta.darfin.entity.disclosure;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

// DisclosureCollectionLog.java
// "이 회사(companyName) + 이 기간(bgnDe~endDe)을 DART에서 실제로 수집 완료했다"는 이력만
// 남기는 테이블. disclosure 테이블에 행이 존재하는지로 "수집 완료 여부"를 추론하면,
// "오늘 올라온 공시" 피드처럼 검색과 무관한 경로로 들어온 행 때문에 좁은 범위(예:
// 오늘 하루)만 실제로 커버됐는데도 훨씬 넓은 검색 범위 전체가 이미 수집된 것으로
// 오판될 수 있다(DisclosureSearchService 참고) — 그래서 수집 이력을 별도로 추적한다.
@Entity
@Table(name = "disclosure_collection_log")
@Getter
@NoArgsConstructor
public class DisclosureCollectionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false)
    private LocalDate bgnDe;

    @Column(nullable = false)
    private LocalDate endDe;

    @Column(nullable = false, updatable = false)
    private LocalDateTime collectedAt = LocalDateTime.now();

    @Builder
    public DisclosureCollectionLog(String companyName, LocalDate bgnDe, LocalDate endDe) {
        this.companyName = companyName;
        this.bgnDe = bgnDe;
        this.endDe = endDe;
    }
}
