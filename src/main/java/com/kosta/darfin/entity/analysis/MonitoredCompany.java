package com.kosta.darfin.entity.analysis;

import com.kosta.darfin.entity.common.Users;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "monitored_companies",
        uniqueConstraints = @UniqueConstraint(name = "uq_monitored_user_corp", columnNames = {"user_id", "corp_code"})
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoredCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "corp_code", nullable = false, length = 8)
    private String corpCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
