package com.kosta.darfin.dto.disclosure;

import org.springframework.data.domain.Page;

public class DisclosureSearchResponse {

    private boolean collected;
    private Integer savedStockCount;
    private Integer savedDisclosureCount;
    // disclosure_type에 등록되지 않은 type_code라 저장이 스킵된 공시 건수.
    private Integer skippedCount;
    private Page<DisclosureSearchResult> results;

    public static DisclosureSearchResponse of(DartCollectResponseDto collectResult, Page<DisclosureSearchResult> results) {
        DisclosureSearchResponse dto = new DisclosureSearchResponse();
        dto.results = results;
        if (collectResult != null) {
            dto.collected = true;
            dto.savedStockCount = collectResult.getSavedStockCount();
            dto.savedDisclosureCount = collectResult.getSavedDisclosureCount();
            dto.skippedCount = collectResult.getSkippedCount();
        }
        return dto;
    }

    public boolean isCollected() {
        return collected;
    }

    public Integer getSavedStockCount() {
        return savedStockCount;
    }

    public Integer getSavedDisclosureCount() {
        return savedDisclosureCount;
    }

    public Integer getSkippedCount() {
        return skippedCount;
    }

    public Page<DisclosureSearchResult> getResults() {
        return results;
    }
}
