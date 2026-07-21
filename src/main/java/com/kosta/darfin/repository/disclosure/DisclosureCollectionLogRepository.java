package com.kosta.darfin.repository.disclosure;

import com.kosta.darfin.entity.disclosure.DisclosureCollectionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface DisclosureCollectionLogRepository extends JpaRepository<DisclosureCollectionLog, Long> {

    /** 요청한 [dateFrom, dateTo] 전체를 이미 포함하는 과거 수집 이력이 있는지 — 부분적으로만
     * 겹치는 이력은 "이미 수집됨"으로 치지 않는다(그 사이 빈 구간은 여전히 DART에서 안 받아온 것). */
    boolean existsByCompanyNameAndBgnDeLessThanEqualAndEndDeGreaterThanEqual(
            String companyName, LocalDate dateFrom, LocalDate dateTo);
}
