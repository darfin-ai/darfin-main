package com.kosta.darfin.repository.payment;

import com.kosta.darfin.entity.payment.TokenTransactions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TokenTransactionsRepository extends JpaRepository<TokenTransactions, Long> {

    List<TokenTransactions> findTop50ByUser_IdOrderByCreatedAtDesc(Long userId);
}
