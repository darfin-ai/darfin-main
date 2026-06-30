package com.kosta.darfin.repository.fund;

import com.kosta.darfin.entity.fund.StockPriceRealtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface StockPriceRealtimeRepository extends JpaRepository<StockPriceRealtime, Long> {

    @Query(value = "SELECT * FROM stock_price_realtime " +
            "WHERE stock_code = :stockCode ORDER BY fetched_at DESC LIMIT 1",
            nativeQuery = true)
    Optional<StockPriceRealtime> findLatestByStockCode(@Param("stockCode") String stockCode);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO stock_price_realtime " +
            "(stock_code, current_price, prev_close_price, change_rate, volume, high_52w, low_52w, fetched_at) " +
            "VALUES (:stockCode, :currentPrice, :prevClosePrice, :changeRate, :volume, :high52w, :low52w, :fetchedAt)",
            nativeQuery = true)
    void insertPrice(@Param("stockCode") String stockCode,
                     @Param("currentPrice") Long currentPrice,
                     @Param("prevClosePrice") Long prevClosePrice,
                     @Param("changeRate") Double changeRate,
                     @Param("volume") Long volume,
                     @Param("high52w") Long high52w,
                     @Param("low52w") Long low52w,
                     @Param("fetchedAt") LocalDateTime fetchedAt);
}