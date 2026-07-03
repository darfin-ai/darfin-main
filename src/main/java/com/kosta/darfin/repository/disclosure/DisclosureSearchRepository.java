package com.kosta.darfin.repository.disclosure;

import com.kosta.darfin.dto.disclosure.DisclosureSearchCondition;
import com.kosta.darfin.dto.disclosure.DisclosureSearchResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;


public interface DisclosureSearchRepository {
    Page<DisclosureSearchResult> search(DisclosureSearchCondition condition, Pageable pageable);

    
    boolean existsCollected(String companyNameOrCode, LocalDate dateFrom, LocalDate dateTo);
}
