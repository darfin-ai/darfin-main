package com.kosta.darfin.repository.disclosure;

import com.kosta.darfin.entity.disclosure.DisclosureType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DisclosureTypeRepository
        extends JpaRepository<DisclosureType, String>,
                QuerydslPredicateExecutor<DisclosureType> {
}
