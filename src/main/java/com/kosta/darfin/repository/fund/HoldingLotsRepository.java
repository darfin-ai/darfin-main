package com.kosta.darfin.repository.fund;

import com.kosta.darfin.entity.fund.HoldingLots;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HoldingLotsRepository extends JpaRepository<HoldingLots, Long> {

    /** FIFO 소진 순서: 오래 산 로트부터 (동시각이면 생성 순) */
    List<HoldingLots> findByUser_IdAndStockInfo_StockCodeOrderByBoughtAtAscLotIdAsc(Long userId, String stockCode);

    void deleteByUser_Id(Long userId);
}
