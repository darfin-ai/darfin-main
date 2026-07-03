package com.kosta.darfin.repository.common;

import com.kosta.darfin.entity.common.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByDartCorpCode(String dartCorpCode);

    boolean existsByDartCorpCode(String dartCorpCode);

    Optional<Stock> findByStockCode(String stockCode);

    List<Stock> findByStockCodeIsNotNullAndCompanyNameContainingIgnoreCaseOrderByCompanyNameAsc(String keyword);
}
