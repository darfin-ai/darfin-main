package com.kosta.darfin.repository.common;

import com.kosta.darfin.entity.common.Stock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByDartCorpCode(String dartCorpCode);

    boolean existsByDartCorpCode(String dartCorpCode);

    Optional<Stock> findByStockCode(String stockCode);

    List<Stock> findByStockCodeIsNotNullAndCompanyNameContainingIgnoreCaseOrderByCompanyNameAsc(String keyword);

    @Query("SELECT s FROM Stock s "
            + "WHERE s.stockCode IS NOT NULL "
            + "AND (LOWER(s.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "     OR s.stockCode LIKE CONCAT(:keyword, '%')) "
            + "ORDER BY s.companyName ASC")
    List<Stock> findListedByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
