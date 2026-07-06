package com.kosta.darfin.dto.analysis;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CompanyListItemResponse {
    private CompanyResponse company;
    private List<ScoreComponentResponse> scores;
}
