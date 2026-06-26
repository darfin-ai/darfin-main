package com.kosta.darfin.entity.Dictionary;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// DictionaryTerm.java
@Entity
@Table(name = "dictionary_term")
@Getter
@NoArgsConstructor
public class DictionaryTerm {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String term;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawDefinition;

    @Column(nullable = false, length = 20)
    private String sourceType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}