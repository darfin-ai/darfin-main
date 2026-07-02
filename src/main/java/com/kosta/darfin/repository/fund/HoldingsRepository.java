package com.kosta.darfin.repository.fund;

import com.kosta.darfin.entity.fund.Holdings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HoldingsRepository extends JpaRepository<Holdings, Long> {

    List<Holdings> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    Optional<Holdings> findByUser_IdAndStockInfo_StockCode(Long userId, String stockCode);

    void deleteByUser_Id(Long userId);
}
