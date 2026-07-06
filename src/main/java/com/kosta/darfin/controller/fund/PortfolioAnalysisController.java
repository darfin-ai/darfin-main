package com.kosta.darfin.controller.fund;

import com.kosta.darfin.service.fund.PortfolioAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
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

    private final PortfolioAnalysisService portfolioAnalysisService;

    @PostMapping
    public Map<String, Object> analyzePortfolio(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, Object> body
    ) {
        return portfolioAnalysisService.analyzeAndSave(usernameOf(user), body);
    }

    @GetMapping("/reports")
    public Map<String, Object> getPortfolioReports(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) Long userId
    ) {
        List<Map<String, Object>> reports = portfolioAnalysisService.listReports(usernameOf(user), limit, userId);
        return Map.of("reports", reports);
    }

    private String usernameOf(UserDetails user) {
        return user == null ? null : user.getUsername();
    }
}
