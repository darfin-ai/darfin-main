package com.kosta.darfin.controller.analysis;

import com.kosta.darfin.dto.analysis.StarredCompanyListResponse;
import com.kosta.darfin.dto.analysis.StarredCompanyResponse;
import com.kosta.darfin.service.analysis.StarredCompanyService;
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
@RequestMapping("/api/v1/companies/starred")
@RequiredArgsConstructor
public class StarredCompanyController {

    private final StarredCompanyService starredCompanyService;

    @GetMapping
    public ResponseEntity<StarredCompanyListResponse> listStarred(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(starredCompanyService.listStarred(userDetails.getUsername()));
    }

    @PostMapping("/{corpCode}")
    public ResponseEntity<StarredCompanyResponse> addStarred(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String corpCode) {
        return ResponseEntity.ok(starredCompanyService.addStarred(userDetails.getUsername(), corpCode));
    }

    @DeleteMapping("/{corpCode}")
    public ResponseEntity<Void> removeStarred(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String corpCode) {
        starredCompanyService.removeStarred(userDetails.getUsername(), corpCode);
        return ResponseEntity.noContent().build();
    }
}
