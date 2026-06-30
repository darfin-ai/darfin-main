package com.kosta.darfin.repository.community;

import com.kosta.darfin.entity.community.CommunityPosts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommunityPostsRepository extends JpaRepository<CommunityPosts, Long> {

    @Query("SELECT p FROM CommunityPosts p " +
           "LEFT JOIN FETCH p.author " +
           "LEFT JOIN FETCH p.stock " +
           "WHERE p.status = 'ACTIVE' " +
           "AND (:search IS NULL OR " +
               "LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
               "LOWER(p.content) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
               "LOWER(p.stock.companyName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY p.createdAt DESC")
    List<CommunityPosts> searchPosts(@Param("search") String search);

    @Query("SELECT p FROM CommunityPosts p " +
           "LEFT JOIN FETCH p.author " +
           "LEFT JOIN FETCH p.stock " +
           "WHERE p.id = :id AND p.status = 'ACTIVE'")
    Optional<CommunityPosts> findActiveById(@Param("id") Long id);
}
