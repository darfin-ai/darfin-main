package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

/**
 * hops는 파이썬 파이프라인이 이미 프론트 ReasoningHop[] 그대로(camelCase) 저장한
 * JSON을 파싱만 해서 그대로 통과시킨다 (재조립 없음).
 */
@Getter
@Builder
public class FindingResponse {
    private String id;
    private String severity;
    private String scoreComponent;
    private String summary;
    private Object hops;
}
