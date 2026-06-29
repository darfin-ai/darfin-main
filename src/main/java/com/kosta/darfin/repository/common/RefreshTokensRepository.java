package com.kosta.darfin.repository.common;

import com.kosta.darfin.entity.common.RefreshTokens;
import com.kosta.darfin.entity.common.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokensRepository extends JpaRepository<RefreshTokens, Long> {

    Optional<RefreshTokens> findByToken(String token);

    // clearAutomatically: 벌크 DELETE 후 1차 캐시 초기화 (stale 데이터 방지)
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshTokens rt WHERE rt.user = :user")
    void deleteByUser(@Param("user") Users user);
}
