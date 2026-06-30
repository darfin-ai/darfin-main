package com.kosta.darfin.dto.community;

import com.kosta.darfin.entity.community.CommunityComments;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AnswerResponse {
    private Long id;
    private String content;
    private Long authorId;
    private String authorNickname;
    private String authorProfileImage;
    private Boolean isAdopted;
    private long replyCount;
    private LocalDateTime createdAt;

    public static AnswerResponse from(CommunityComments comment, long replyCount) {
        return AnswerResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .authorId(comment.getAuthor().getId())
                .authorNickname(comment.getAuthor().getNickname())
                .authorProfileImage(comment.getAuthor().getProfileImage())
                .isAdopted(comment.getIsAdopted())
                .replyCount(replyCount)
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
