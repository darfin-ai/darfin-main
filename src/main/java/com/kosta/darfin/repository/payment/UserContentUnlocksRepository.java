package com.kosta.darfin.repository.payment;

import com.kosta.darfin.entity.payment.UserContentUnlocks;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserContentUnlocksRepository extends JpaRepository<UserContentUnlocks, Long> {

    boolean existsByUser_IdAndFeatureTypeAndResourceId(Long userId, String featureType, String resourceId);
}
