package com.kosta.darfin.repository.payment;

import com.kosta.darfin.entity.payment.Subscriptions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionsRepository extends JpaRepository<Subscriptions, Long> {

    Optional<Subscriptions> findByUser_Id(Long userId);

    List<Subscriptions> findByStatusAndNextPaymentDate(String status, LocalDate nextPaymentDate);

    List<Subscriptions> findByStatusInAndNextPaymentDate(List<String> statuses, LocalDate nextPaymentDate);
}
