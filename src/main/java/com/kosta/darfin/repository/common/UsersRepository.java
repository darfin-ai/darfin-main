package com.kosta.darfin.repository.common;

import com.kosta.darfin.entity.common.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Long> {

    Optional<Users> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Users> findByProviderAndProviderUserId(String provider, String providerUserId);

    List<Users> findByNameAndPhoneOrderByCreatedAtAsc(String name, String phone);

    List<Users> findBySubscriptionLevelIn(List<String> subscriptionLevels);

    // 토큰 차감 동시성 제어용 (같은 유저에 대한 동시 과금 요청 직렬화)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Users u where u.id = :id")
    Optional<Users> findByIdForUpdate(@Param("id") Long id);
}
