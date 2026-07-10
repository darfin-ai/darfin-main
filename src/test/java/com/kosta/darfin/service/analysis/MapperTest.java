package com.kosta.darfin.service.analysis;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MapperTest {

    private final Mapper mapper = new Mapper();

    // ---- isPlaceholderOnly ----

    @Test
    void isPlaceholderOnlyReturnsTrueForNullOrEmptyList() {
        assertThat(mapper.isPlaceholderOnly(null)).isTrue();
        assertThat(mapper.isPlaceholderOnly(List.of())).isTrue();
    }

    @Test
    void isPlaceholderOnlyReturnsTrueWhenAllNonMetaFieldsArePlaceholders() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("se", "합계");
        row.put("rcept_no", "20240101000123");
        row.put("thstrm", "-");
        row.put("frmtrm", "－");
        row.put("lwfr", "");

        assertThat(mapper.isPlaceholderOnly(List.of(row))).isTrue();
    }

    @Test
    void isPlaceholderOnlyReturnsFalseWhenOneRealValueExists() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("se", "합계");
        row.put("thstrm", "-");
        row.put("frmtrm", "1,234");

        assertThat(mapper.isPlaceholderOnly(List.of(row))).isFalse();
    }

    @Test
    void isPlaceholderOnlyIgnoresMetaAndLabelKeysEvenWithRealValues() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("se", "합계");
        row.put("rcept_no", "20240101000123");
        row.put("corp_cls", "Y");
        row.put("corp_code", "00126380");
        row.put("corp_name", "삼성전자");
        row.put("stlm_dt", "2024-12-31");
        row.put("rm", "비고");
        row.put("thstrm", "-");
        row.put("frmtrm", "-");

        assertThat(mapper.isPlaceholderOnly(List.of(row))).isTrue();
    }

    // ---- mapRows / mapRow ----

    @Test
    void mapRowUsesFieldAliasesForCamelCaseKeys() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bsis_posesn_stock_co", "100");
        row.put("fo_bbm", "홍길동");

        List<Map<String, Object>> mapped = mapper.mapRows(List.of(row), "hyslrSttus");

        assertThat(mapped).hasSize(1);
        Map<String, Object> out = mapped.get(0);
        assertThat(out).containsKey("bsisPosesnStockCo");
        assertThat(out.get("bsisPosesnStockCo")).isEqualTo(100L);
        assertThat(out).containsEntry("foBbm", "홍길동");
    }

    @Test
    void mapRowParsesCommaFormattedNumericField() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("thstrm", "1,234,567");

        Map<String, Object> out = mapper.mapRows(List.of(row), "irdsSttus").get(0);

        assertThat(out.get("thstrm")).isEqualTo(1234567L);
    }

    @Test
    void mapRowNormalizesDottedDateField() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("change_on", "2025.12.31");

        Map<String, Object> out = mapper.mapRows(List.of(row), "hyslrChgSttus").get(0);

        assertThat(out.get("changeOn")).isEqualTo("2025-12-31");
    }

    @Test
    void mapRowScalesAlotMatterThousandKrwWhenSeContainsMillionWon() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("se", "주당 배당금(원) (백만원)");
        row.put("thstrm", "5");

        Map<String, Object> out = mapper.mapRows(List.of(row), "alotMatter").get(0);

        assertThat(out.get("thstrm")).isEqualTo(5_000_000L);
    }

    @Test
    void mapRowDoesNotScaleAlotMatterWhenSeDoesNotContainMillionWon() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("se", "주당 배당금(원)");
        row.put("thstrm", "5");

        Map<String, Object> out = mapper.mapRows(List.of(row), "alotMatter").get(0);

        assertThat(out.get("thstrm")).isEqualTo(5L);
    }

    @Test
    void mapRowStripsMetaKeysFromOutput() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rcept_no", "20240101000123");
        row.put("corp_code", "00126380");
        row.put("se", "합계");

        Map<String, Object> out = mapper.mapRows(List.of(row), "stockTotqySttus").get(0);

        assertThat(out).doesNotContainKeys("rcept_no", "corp_code", "rceptNo", "corpCode");
        assertThat(out).containsEntry("se", "합계");
    }

    @Test
    void mapRowKeepsBsnsYearAsPlainStringForAuditOpinionApiId() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bsns_year", "2024");

        Map<String, Object> out = mapper.mapRows(List.of(row), "accnutAdtorNmNdAdtOpinion").get(0);

        assertThat(out.get("bsnsYear")).isEqualTo("2024");
        assertThat(out.get("bsnsYear")).isInstanceOf(String.class);
    }
}
