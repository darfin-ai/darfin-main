package com.kosta.darfin.entity.payment;

import com.kosta.darfin.entity.common.Users;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// UserContentUnlocks.java
// 공시요약/분석·기업분석을 "최초 1회 차감, 이후 재열람 무료"로 과금하기 위한 열람권 원장.
// featureType: DISCLOSURE(resourceId=rceptNo) / COMPANY(resourceId=corpCode)
@Entity
@Table(name = "user_content_unlocks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "feature_type", "resource_id"}))
@Getter
@NoArgsConstructor
public class UserContentUnlocks {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "feature_type", nullable = false, length = 30)
    private String featureType;

    @Column(name = "resource_id", nullable = false, length = 100)
    private String resourceId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public UserContentUnlocks(Users user, String featureType, String resourceId) {
        this.user = user;
        this.featureType = featureType;
        this.resourceId = resourceId;
    }
}
