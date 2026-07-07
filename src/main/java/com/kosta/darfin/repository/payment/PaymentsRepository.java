package com.kosta.darfin.repository.payment;

import com.kosta.darfin.entity.payment.Payments;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentsRepository extends JpaRepository<Payments, Long> {

    List<Payments> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<Payments> findByMerchantUid(String merchantUid);

    Optional<Payments> findByIdAndUser_Id(Long id, Long userId);
}
