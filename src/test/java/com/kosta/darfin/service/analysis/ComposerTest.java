package com.kosta.darfin.service.analysis;

import com.kosta.darfin.dto.analysis.DartOverviewResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComposerTest {

    private final Composer composer = new Composer(new Mapper());

    @Test
    void allEmptyPayloadsProduceAllNullSections() {
        DartOverviewResponse response = composer.compose(
                "2025", "11011", "R1", Map.of(), Map.of());

        assertThat(response.getMeta().getBsnsYear()).isEqualTo("2025");
        assertThat(response.getMeta().getReprtCode()).isEqualTo("11011");
        assertThat(response.getMeta().getRceptNo()).isEqualTo("R1");
        assertThat(response.getDividends()).isNull();
        assertThat(response.getMajorShareholders()).isNull();
        assertThat(response.getMajorShareholderChanges()).isNull();
        assertThat(response.getMinorityShareholders()).isNull();
        assertThat(response.getEmployees()).isNull();
        assertThat(response.getTreasuryStock()).isNull();
        assertThat(response.getCapitalChanges()).isNull();
        assertThat(response.getStockTotals()).isNull();
        assertThat(response.getExecutives()).isNull();
        assertThat(response.getAuditOpinions()).isNull();
    }

    @Test
    void oneSectionWithRowsIsPopulatedOthersStayNull() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("se", "주당현금배당금(원)");
        row.put("thstrm", "500");

        Map<String, List<Map<String, Object>>> payloads = Map.of("alotMatter", List.of(row));

        DartOverviewResponse response = composer.compose("2025", "11011", "R1", payloads, Map.of());

        assertThat(response.getDividends()).isNotNull();
        assertThat(response.getDividends().getRows()).hasSize(1);
        assertThat(response.getDividends().getSourceRef().getSourceRef()).isEqualTo("R1");
        assertThat(response.getDividends().getAsOf()).isNull();
        assertThat(response.getMajorShareholders()).isNull();
    }

    @Test
    void fallbackInfoPopulatesAsOfAndUsesFallbackRceptNoInSourceRef() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fo_bbm", "관리부");
        row.put("sm", "10");

        Map<String, List<Map<String, Object>>> payloads = Map.of("empSttus", List.of(row));
        Composer.FallbackInfo fallback = new Composer.FallbackInfo("2024", "11011", "OLD_RCEPT");
        Map<String, Composer.FallbackInfo> fallbackInfo = Map.of("empSttus", fallback);

        DartOverviewResponse response = composer.compose("2025", "11013", "CURRENT_RCEPT", payloads, fallbackInfo);

        assertThat(response.getEmployees().getAsOf()).isNotNull();
        assertThat(response.getEmployees().getAsOf().getBsnsYear()).isEqualTo("2024");
        assertThat(response.getEmployees().getAsOf().getRceptNo()).isEqualTo("OLD_RCEPT");
        assertThat(response.getEmployees().getSourceRef().getSourceRef()).isEqualTo("OLD_RCEPT");
    }

    @Test
    void auditOpinionsDedupesIdenticalRowsOtherSectionsDoNot() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("adt_opinion", "적정");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("adt_opinion", "적정");

        Map<String, List<Map<String, Object>>> auditPayloads =
                Map.of("accnutAdtorNmNdAdtOpinion", List.of(row1, row2));
        DartOverviewResponse auditResponse = composer.compose("2025", "11011", "R1", auditPayloads, Map.of());
        assertThat(auditResponse.getAuditOpinions().getRows()).hasSize(1);

        Map<String, Object> execRow1 = new LinkedHashMap<>();
        execRow1.put("nm", "홍길동");
        Map<String, Object> execRow2 = new LinkedHashMap<>();
        execRow2.put("nm", "홍길동");
        Map<String, List<Map<String, Object>>> execPayloads =
                Map.of("exctvSttus", List.of(execRow1, execRow2));
        DartOverviewResponse execResponse = composer.compose("2025", "11011", "R1", execPayloads, Map.of());
        assertThat(execResponse.getExecutives().getRows()).hasSize(2);
    }

    @Test
    void nullRceptNoBecomesEmptyStringInMeta() {
        DartOverviewResponse response = composer.compose("2025", "11011", null, Map.of(), Map.of());
        assertThat(response.getMeta().getRceptNo()).isEqualTo("");
    }
}
