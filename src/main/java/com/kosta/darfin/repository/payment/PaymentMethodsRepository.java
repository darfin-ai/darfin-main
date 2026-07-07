package com.kosta.darfin.repository.payment;

import com.kosta.darfin.entity.payment.PaymentMethods;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodsRepository extends JpaRepository<PaymentMethods, Long> {

    List<PaymentMethods> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<PaymentMethods> findByUser_IdAndIsDefaultTrue(Long userId);

    Optional<PaymentMethods> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_Id(Long userId);
}
