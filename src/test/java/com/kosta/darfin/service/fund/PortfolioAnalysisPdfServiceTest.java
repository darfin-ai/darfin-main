package com.kosta.darfin.service.fund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.darfin.entity.fund.AiReports;
import com.kosta.darfin.repository.fund.AiReportsRepository;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioAnalysisPdfServiceTest {

    @Test
    void createReportPdfReturnsReadablePdf() throws Exception {
        AiReportsRepository repository = mock(AiReportsRepository.class);
        AiReports report = AiReports.builder()
                .reportId(7L)
                .healthScore("82")
                .tendencyLabel("균형형")
                .reportContent("{"
                        + "\"report\":{\"label\":\"균형형\",\"health\":{\"total\":82}},"
                        + "\"analysis\":\"리스크와 수익의 균형이 양호합니다.\","
                        + "\"metrics\":{\"behavior\":{\"tradesPerMonth\":4,\"avgHoldDays\":12}}"
                        + "}")
                .createdAt(LocalDateTime.of(2026, 7, 8, 9, 30))
                .build();
        when(repository.findByReportIdAndUser_Email(7L, "user@example.com")).thenReturn(Optional.of(report));

        PortfolioAnalysisPdfService pdfService = new PortfolioAnalysisPdfService(repository, new ObjectMapper(), "");
        byte[] pdf = pdfService.createReportPdf("user@example.com", 7L);

        assertThat(pdf).startsWith("%PDF".getBytes());
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        } finally {
            reader.close();
        }
    }
}
