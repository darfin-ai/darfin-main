package com.kosta.darfin.repository.fund;

import com.kosta.darfin.entity.fund.Trades;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradesRepository extends JpaRepository<Trades, Long> {

    List<Trades> findByUser_IdOrderByTradedAtDesc(Long userId);
}
