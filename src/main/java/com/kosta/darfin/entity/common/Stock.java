package com.kosta.darfin.entity.common;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String marketType;

    @Column(nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false, length = 20, unique = true)
    private String dartCorpCode;

    @Column(length = 20, unique = true)
    private String stockCode;
}
