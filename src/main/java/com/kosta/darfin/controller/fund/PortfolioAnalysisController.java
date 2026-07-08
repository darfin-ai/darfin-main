package com.kosta.darfin.controller.fund;

import com.kosta.darfin.service.fund.PortfolioAnalysisService;
import com.kosta.darfin.service.fund.PortfolioAnalysisPdfService;
import com.kosta.darfin.service.payment.FeatureType;
import com.kosta.darfin.service.payment.TokenBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis/portfolio")
@RequiredArgsConstructor
public class PortfolioAnalysisController {

    private static final int REPORT_TOKEN_COST = 2000;

    private final PortfolioAnalysisService portfolioAnalysisService;
    private final PortfolioAnalysisPdfService portfolioAnalysisPdfService;
    private final TokenBillingService tokenBillingService;

    @PostMapping
    public Map<String, Object> analyzePortfolio(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, Object> body
    ) {
        String email = usernameOf(user);
        // 외부 LLM 호출 전에 잔액을 미리 확인해 불필요한 호출을 막고, 리포트 생성이
        // 실제로 성공한 뒤에만(예외 없이 반환된 경우에만) 토큰을 차감한다.
        tokenBillingService.assertSufficientBalance(email, REPORT_TOKEN_COST);
        Map<String, Object> result = portfolioAnalysisService.analyzeAndSave(email, body);
        tokenBillingService.chargeForAction(email, FeatureType.PORTFOLIO_REPORT, REPORT_TOKEN_COST);
        return result;
    }

    @GetMapping("/reports")
    public Map<String, Object> getPortfolioReports(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        List<Map<String, Object>> reports = portfolioAnalysisService.listReports(usernameOf(user), limit);
        return Map.of("reports", reports);
    }

    @GetMapping({"/reports/{reportId}.pdf", "/reports/{reportId}/download"})
    public ResponseEntity<byte[]> downloadPortfolioReportPdf(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long reportId
    ) {
        byte[] pdf = portfolioAnalysisPdfService.createReportPdf(usernameOf(user), reportId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"portfolio-report-" + reportId + ".pdf\"")
                .body(pdf);
    }

    private String usernameOf(UserDetails user) {
        return user == null ? null : user.getUsername();
    }
}
