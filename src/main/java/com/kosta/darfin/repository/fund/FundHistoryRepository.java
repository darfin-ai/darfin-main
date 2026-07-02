package com.kosta.darfin.repository.fund;

import com.kosta.darfin.entity.fund.FundHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundHistoryRepository extends JpaRepository<FundHistory, Long> {
}
