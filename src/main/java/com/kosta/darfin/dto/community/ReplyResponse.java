package com.kosta.darfin.dto.community;

import com.kosta.darfin.entity.community.CommunityComments;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReplyResponse {
    private Long id;
    private String content;
    private Long authorId;
    private String authorNickname;
    private String authorProfileImage;
    private LocalDateTime createdAt;

    public static ReplyResponse from(CommunityComments comment) {
        return ReplyResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .authorId(comment.getAuthor().getId())
                .authorNickname(comment.getAuthor().getNickname())
                .authorProfileImage(comment.getAuthor().getProfileImage())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
