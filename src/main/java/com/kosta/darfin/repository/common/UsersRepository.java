package com.kosta.darfin.repository.common;

import com.kosta.darfin.entity.common.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Long> {

    Optional<Users> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Users> findByProviderAndProviderUserId(String provider, String providerUserId);

    List<Users> findByNameAndPhoneOrderByCreatedAtAsc(String name, String phone);
}
