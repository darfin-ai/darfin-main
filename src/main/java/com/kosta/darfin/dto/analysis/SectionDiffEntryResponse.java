package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

/**
 * metrics는 파이썬 diff 엔진이 이미 프론트 NumericDeltaMetric[] 형태(label/
 * current/baseline/unit)로 저장한 JSON을 파싱만 해서 통과시킨다.
 */
@Getter
@Builder
public class SectionDiffEntryResponse {
    private String sectionLabel;
    private String sourceLabel;
    private String comparisonType;
    private String changeType;
    private String before;
    private String after;
    private Object metrics;
    private String sourceRef;
}
