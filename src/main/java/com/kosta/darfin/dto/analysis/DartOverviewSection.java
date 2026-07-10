package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class DartOverviewSection {
    private List<Map<String, Object>> rows;
    private SourceRef sourceRef;
    private AsOf asOf; // null unless this section was backfilled from an older period (fallback)

    @Getter
    @Builder
    public static class SourceRef {
        private String sectionLabel;
        private String excerpt;
        private String sourceRef; // rcept_no this section's data was sourced from
    }

    @Getter
    @Builder
    public static class AsOf {
        private String bsnsYear;
        private String reprtCode;
        private String rceptNo;
    }
}
