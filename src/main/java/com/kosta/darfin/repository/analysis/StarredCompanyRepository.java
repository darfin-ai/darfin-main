package com.kosta.darfin.repository.analysis;

import com.kosta.darfin.entity.analysis.StarredCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StarredCompanyRepository extends JpaRepository<StarredCompany, Long> {

    List<StarredCompany> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<StarredCompany> findByUser_IdAndCorpCode(Long userId, String corpCode);

    void deleteByUser_IdAndCorpCode(Long userId, String corpCode);
}
