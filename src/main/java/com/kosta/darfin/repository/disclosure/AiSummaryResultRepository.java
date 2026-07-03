package com.kosta.darfin.repository.disclosure;

import com.kosta.darfin.entity.disclosure.AiSummaryResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AiSummaryResultRepository
        extends JpaRepository<AiSummaryResult, String>,
                QuerydslPredicateExecutor<AiSummaryResult> {
}
