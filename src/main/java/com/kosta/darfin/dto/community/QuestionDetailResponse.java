package com.kosta.darfin.dto.community;

import com.kosta.darfin.entity.community.CommunityPosts;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class QuestionDetailResponse {
    private Long id;
    private String title;
    private String content;
    private Long authorId;
    private String authorNickname;
    private String authorProfileImage;
    private StockSearchResponse stock;
    private Integer views;
    private Boolean isResolved;
    private long answerCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static QuestionDetailResponse from(CommunityPosts post, long answerCount) {
        return QuestionDetailResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .authorId(post.getAuthor().getId())
                .authorNickname(post.getAuthor().getNickname())
                .authorProfileImage(post.getAuthor().getProfileImage())
                .stock(post.getStock() != null ? StockSearchResponse.from(post.getStock()) : null)
                .views(post.getViews())
                .isResolved(post.getIsResolved())
                .answerCount(answerCount)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
