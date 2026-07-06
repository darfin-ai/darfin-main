package com.kosta.darfin.controller.analysis;

import com.kosta.darfin.dto.analysis.CompanyDetailResponse;
import com.kosta.darfin.dto.analysis.CompanyListItemResponse;
import com.kosta.darfin.service.analysis.CompanyAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyAnalysisService companyAnalysisService;

    /**
     * 기업 목록 + 최신 scores + changeSummary
     * GET /api/v1/companies
     */
    @GetMapping
    public ResponseEntity<List<CompanyListItemResponse>> listCompanies() {
        return ResponseEntity.ok(companyAnalysisService.listCompanies());
    }

    /**
     * 기업 상세 (개요/재무추이/공시변경 탭 데이터 전부)
     * GET /api/v1/companies/{corpCode}
     */
    @GetMapping("/{corpCode}")
    public ResponseEntity<CompanyDetailResponse> getCompanyDetail(@PathVariable String corpCode) {
        CompanyDetailResponse detail = companyAnalysisService.getCompanyDetail(corpCode);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }
}
