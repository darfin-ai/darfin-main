package com.kosta.darfin.repository.disclosure;

import com.kosta.darfin.entity.disclosure.AiAnalysisItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiAnalysisItemRepository
        extends JpaRepository<AiAnalysisItem, Long>,
                QuerydslPredicateExecutor<AiAnalysisItem> {

    
    List<AiAnalysisItem> findByDisclosure_RceptNoOrderByItemNoAsc(String rceptNo);

    
    void deleteByDisclosure_RceptNo(String rceptNo);

    boolean existsByDisclosure_RceptNo(String rceptNo);
}
