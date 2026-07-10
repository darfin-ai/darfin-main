package com.kosta.darfin.service.analysis;

import com.kosta.darfin.dto.analysis.DartOverviewResponse;
import com.kosta.darfin.dto.analysis.DartOverviewSection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class Composer {

    private final Mapper mapper;

    /** One entry per api_id that was backfilled from an older period. */
    @Getter
    @RequiredArgsConstructor
    public static class FallbackInfo {
        private final String bsnsYear;
        private final String reprtCode;
        private final String rceptNo;
    }

    public DartOverviewResponse compose(
            String bsnsYear,
            String reprtCode,
            String rceptNo,
            Map<String, List<Map<String, Object>>> payloads, // apiId -> raw rows, or null/absent for "no data"
            Map<String, FallbackInfo> fallbackInfo // apiId -> asOf info, only for backfilled sections
    ) {
        Map<String, DartOverviewSection> sections = new HashMap<>();
        for (String apiId : ReportFactApiIds.ALL) {
            String sectionKey = ReportFactApiIds.API_TO_SECTION.get(apiId);
            List<Map<String, Object>> raw = payloads.get(apiId);
            FallbackInfo asOf = fallbackInfo.get(apiId);
            sections.put(sectionKey, buildSection(raw, apiId, sectionKey, rceptNo, asOf));
        }

        return DartOverviewResponse.builder()
                .meta(DartOverviewResponse.Meta.builder()
                        .bsnsYear(bsnsYear)
                        .reprtCode(reprtCode)
                        .rceptNo(rceptNo == null ? "" : rceptNo)
                        .build())
                .dividends(sections.get("dividends"))
                .majorShareholders(sections.get("majorShareholders"))
                .majorShareholderChanges(sections.get("majorShareholderChanges"))
                .minorityShareholders(sections.get("minorityShareholders"))
                .employees(sections.get("employees"))
                .treasuryStock(sections.get("treasuryStock"))
                .capitalChanges(sections.get("capitalChanges"))
                .stockTotals(sections.get("stockTotals"))
                .executives(sections.get("executives"))
                .auditOpinions(sections.get("auditOpinions"))
                .build();
    }

    private DartOverviewSection buildSection(
            List<Map<String, Object>> raw, String apiId, String sectionKey, String rceptNo, FallbackInfo asOf
    ) {
        if (raw == null || raw.isEmpty()) return null;

        List<Map<String, Object>> mapped = mapper.mapRows(raw, apiId);
        if ("auditOpinions".equals(sectionKey)) {
            mapped = dedupe(mapped);
        }

        String label = ReportFactApiIds.SECTION_LABELS.get(sectionKey);
        String sourceRcept = asOf != null ? asOf.getRceptNo() : rceptNo;
        DartOverviewSection.SourceRef sourceRef = sourceRcept == null ? null :
                DartOverviewSection.SourceRef.builder()
                        .sectionLabel(label)
                        .excerpt(label + " (DART 정기공시 API)")
                        .sourceRef(sourceRcept)
                        .build();

        DartOverviewSection.DartOverviewSectionBuilder sectionBuilder = DartOverviewSection.builder()
                .rows(mapped)
                .sourceRef(sourceRef);
        if (asOf != null) {
            sectionBuilder.asOf(DartOverviewSection.AsOf.builder()
                    .bsnsYear(asOf.getBsnsYear())
                    .reprtCode(asOf.getReprtCode())
                    .rceptNo(asOf.getRceptNo())
                    .build());
        }
        return sectionBuilder.build();
    }

    /** Drop byte-identical mapped rows (used for auditOpinions), preserve first-occurrence order. */
    private List<Map<String, Object>> dedupe(List<Map<String, Object>> rows) {
        Set<Map<String, Object>> seen = new LinkedHashSet<>();
        return rows.stream().filter(seen::add).collect(Collectors.toList());
    }
}
