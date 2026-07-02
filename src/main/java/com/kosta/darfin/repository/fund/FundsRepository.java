package com.kosta.darfin.repository.fund;

import com.kosta.darfin.entity.fund.Funds;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FundsRepository extends JpaRepository<Funds, Long> {

    Optional<Funds> findByUser_Id(Long userId);
}
