package com.kosta.darfin.repository.community;

import com.kosta.darfin.entity.community.CommunityComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommunityCommentsRepository extends JpaRepository<CommunityComments, Long> {

    // 특정 질문의 답변 목록 (parent가 null인 최상위 댓글)
    @Query("SELECT c FROM CommunityComments c " +
           "LEFT JOIN FETCH c.author " +
           "WHERE c.post.id = :postId AND c.parent IS NULL " +
           "ORDER BY c.createdAt ASC")
    List<CommunityComments> findAnswersByPostId(@Param("postId") Long postId);

    // 특정 답변의 대댓글 목록
    @Query("SELECT c FROM CommunityComments c " +
           "LEFT JOIN FETCH c.author " +
           "WHERE c.parent.id = :answerId " +
           "ORDER BY c.createdAt ASC")
    List<CommunityComments> findRepliesByParentId(@Param("answerId") Long answerId);

    // 질문의 답변 수
    @Query("SELECT COUNT(c) FROM CommunityComments c " +
           "WHERE c.post.id = :postId AND c.parent IS NULL")
    long countAnswersByPostId(@Param("postId") Long postId);
}
