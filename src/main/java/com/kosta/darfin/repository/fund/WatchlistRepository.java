package com.kosta.darfin.repository.fund;

import com.kosta.darfin.entity.fund.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<Watchlist> findByUser_IdAndStockInfo_StockCode(Long userId, String stockCode);

    void deleteByUser_IdAndStockInfo_StockCode(Long userId, String stockCode);
}
