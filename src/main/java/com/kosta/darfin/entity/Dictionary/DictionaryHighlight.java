package com.kosta.darfin.entity.Dictionary;

import com.kosta.darfin.entity.disclosure.Disclosure;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

// DictionaryHighlight.java
@Entity
@Table(name = "dictionary_highlight")
@Getter
@NoArgsConstructor
public class DictionaryHighlight {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rcept_no", nullable = false)
    private Disclosure disclosure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private DictionaryTerm term;

    private Integer startIndex;
    private Integer endIndex;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}