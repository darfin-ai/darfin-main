package com.kosta.darfin.repository.analysis;

import com.kosta.darfin.entity.analysis.MonitoredCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonitoredCompanyRepository extends JpaRepository<MonitoredCompany, Long> {

    List<MonitoredCompany> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<MonitoredCompany> findByUser_IdAndCorpCode(Long userId, String corpCode);

    int countByUser_Id(Long userId);

    void deleteByUser_IdAndCorpCode(Long userId, String corpCode);
}
