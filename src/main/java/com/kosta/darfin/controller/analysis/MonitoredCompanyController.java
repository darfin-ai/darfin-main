package com.kosta.darfin.controller.analysis;

import com.kosta.darfin.dto.analysis.MonitoredCompanyListResponse;
import com.kosta.darfin.dto.analysis.MonitoredCompanyResponse;
import com.kosta.darfin.service.analysis.MonitoredCompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/monitored")
@RequiredArgsConstructor
public class MonitoredCompanyController {

    private final MonitoredCompanyService monitoredCompanyService;

    @GetMapping
    public ResponseEntity<MonitoredCompanyListResponse> listMonitored(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(monitoredCompanyService.listMonitored(userDetails.getUsername()));
    }

    @PostMapping("/{corpCode}")
    public ResponseEntity<MonitoredCompanyResponse> addMonitored(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String corpCode) {
        return ResponseEntity.ok(monitoredCompanyService.addMonitored(userDetails.getUsername(), corpCode));
    }

    @DeleteMapping("/{corpCode}")
    public ResponseEntity<Void> removeMonitored(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String corpCode) {
        monitoredCompanyService.removeMonitored(userDetails.getUsername(), corpCode);
        return ResponseEntity.noContent().build();
    }
}
