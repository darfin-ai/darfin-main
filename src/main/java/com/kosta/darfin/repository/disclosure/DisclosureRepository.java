package com.kosta.darfin.repository.disclosure;

import com.kosta.darfin.entity.disclosure.Disclosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;


@Repository
public interface DisclosureRepository extends
        JpaRepository<Disclosure, String>,
        QuerydslPredicateExecutor<Disclosure>,
        DisclosureSearchRepository {
}
