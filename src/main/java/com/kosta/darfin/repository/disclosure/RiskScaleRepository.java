package com.kosta.darfin.repository.disclosure;

import com.kosta.darfin.entity.disclosure.RiskScale;
import com.kosta.darfin.entity.disclosure.RiskScaleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskScaleRepository
        extends JpaRepository<RiskScale, RiskScaleId>,
                QuerydslPredicateExecutor<RiskScale> {

    default Optional<RiskScale> findByCodeAndLabel(String riskScaleCode, String riskLabel) {
        return findById(new RiskScaleId(riskScaleCode, riskLabel));
    }
}
