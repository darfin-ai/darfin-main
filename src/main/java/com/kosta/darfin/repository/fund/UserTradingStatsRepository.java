package com.kosta.darfin.repository.fund;

import com.kosta.darfin.entity.fund.UserTradingStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTradingStatsRepository extends JpaRepository<UserTradingStats, Long> {
    Optional<UserTradingStats> findByUser_Id(Long userId);
}
