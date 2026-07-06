package com.kosta.darfin.entity.fund;

import com.kosta.darfin.entity.common.Users;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// AiReports.java
@Entity
@Table(name = "ai_reports")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiReports {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(columnDefinition = "json")
    private String healthScore;

    @Column(length = 50)
    private String tendencyLabel;

    @Column(columnDefinition = "TEXT")
    private String reportContent;

    @Column(length = 64)
    private String shareToken;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
