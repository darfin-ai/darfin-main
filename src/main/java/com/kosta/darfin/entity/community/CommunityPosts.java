package com.kosta.darfin.entity.community;

import com.kosta.darfin.entity.common.Stock;
import com.kosta.darfin.entity.common.Users;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "community_posts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityPosts {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Users author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder.Default
    @Column(nullable = false)
    private Integer views = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isResolved = false;

    @Builder.Default
    @Column(nullable = false)
    private Integer rewardTokens = 0;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public void update(String title, String content, Stock stock) {
        this.title = title;
        this.content = content;
        this.stock = stock;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementViews() {
        this.views++;
    }

    public void resolve() {
        this.isResolved = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void delete() {
        this.status = "DELETED";
        this.updatedAt = LocalDateTime.now();
    }
}
