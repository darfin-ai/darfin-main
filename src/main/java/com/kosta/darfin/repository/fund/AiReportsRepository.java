package com.kosta.darfin.repository.fund;

import com.kosta.darfin.entity.fund.AiReports;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiReportsRepository extends JpaRepository<AiReports, Long> {
    List<AiReports> findByUser_IdOrderByCreatedAtDescReportIdDesc(Long userId);
}
