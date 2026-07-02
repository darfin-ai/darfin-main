package com.kosta.darfin.repository.fund;

import com.kosta.darfin.entity.fund.FundHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FundHistoryRepository extends JpaRepository<FundHistory, Long> {

    List<FundHistory> findByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByUser_IdAndTypeAndCreatedAtAfter(Long userId, String type, LocalDateTime after);
}
