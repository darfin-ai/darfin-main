package com.kosta.darfin.entity.community;

import com.kosta.darfin.entity.common.Stock;
import com.kosta.darfin.entity.common.Users;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// CommunityPosts.java
@Entity
@Table(name = "community_posts")
@Getter
@NoArgsConstructor
public class CommunityPosts {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Users author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", referencedColumnName = "stockCode")
    private Stock stock;

    @Column(nullable = false)
    private Integer rewardTokens = 0;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}