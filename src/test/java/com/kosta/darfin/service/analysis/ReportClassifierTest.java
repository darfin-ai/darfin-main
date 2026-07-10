package com.kosta.darfin.service.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportClassifierTest {

    private final ReportClassifier classifier = new ReportClassifier();

    @Test
    void classifiesAnnualReport() {
        ReportClassifier.Classified result = classifier.classify("사업보고서 (2025.12)");

        assertThat(result).isNotNull();
        assertThat(result.getReprtCode()).isEqualTo("11011");
        assertThat(result.getBsnsYear()).isEqualTo("2025");
    }

    @Test
    void classifiesHalfYearReport() {
        ReportClassifier.Classified result = classifier.classify("반기보고서 (2025.06)");

        assertThat(result).isNotNull();
        assertThat(result.getReprtCode()).isEqualTo("11012");
        assertThat(result.getBsnsYear()).isEqualTo("2025");
    }

    @Test
    void classifiesFirstQuarterReport() {
        ReportClassifier.Classified result = classifier.classify("분기보고서 (2025.03)");

        assertThat(result).isNotNull();
        assertThat(result.getReprtCode()).isEqualTo("11013");
        assertThat(result.getBsnsYear()).isEqualTo("2025");
    }

    @Test
    void classifiesThirdQuarterReport() {
        ReportClassifier.Classified result = classifier.classify("분기보고서 (2025.09)");

        assertThat(result).isNotNull();
        assertThat(result.getReprtCode()).isEqualTo("11014");
        assertThat(result.getBsnsYear()).isEqualTo("2025");
    }

    @Test
    void matchesEvenWithCorrectionPrefix() {
        ReportClassifier.Classified result = classifier.classify("[기재정정]사업보고서 (2025.12)");

        assertThat(result).isNotNull();
        assertThat(result.getReprtCode()).isEqualTo("11011");
        assertThat(result.getBsnsYear()).isEqualTo("2025");
    }

    @Test
    void returnsNullForInvalidQuarterMonth() {
        ReportClassifier.Classified result = classifier.classify("분기보고서 (2025.06)");

        assertThat(result).isNull();
    }

    @Test
    void returnsNullForUnrelatedReportName() {
        ReportClassifier.Classified result = classifier.classify("주요사항보고서 (2025.06)");

        assertThat(result).isNull();
    }
}
