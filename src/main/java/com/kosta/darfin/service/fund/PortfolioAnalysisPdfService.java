package com.kosta.darfin.service.fund;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.darfin.entity.fund.AiReports;
import com.kosta.darfin.repository.fund.AiReportsRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioAnalysisPdfService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] FONT_CANDIDATES = {
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/Library/Fonts/Arial Unicode.ttf",
            "/System/Library/Fonts/AppleSDGothicNeo.ttc,0",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc,0",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
            "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
            "C:/Windows/Fonts/malgun.ttf"
    };

    private final AiReportsRepository aiReportsRepository;
    private final ObjectMapper objectMapper;
    private final String configuredFontPath;

    public PortfolioAnalysisPdfService(AiReportsRepository aiReportsRepository,
                                       ObjectMapper objectMapper,
                                       @Value("${darfin.pdf.font-path:}") String configuredFontPath) {
        this.aiReportsRepository = aiReportsRepository;
        this.objectMapper = objectMapper;
        this.configuredFontPath = configuredFontPath;
    }

    public byte[] createReportPdf(String email, Long reportId) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }

        AiReports report = aiReportsRepository.findByReportIdAndUser_Email(reportId, email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI 리포트를 찾을 수 없습니다."));
        Map<String, Object> content = parseJsonObject(report.getReportContent());

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 42, 42, 44, 44);
            PdfWriter.getInstance(document, out);
            document.addTitle("Darfin AI 분석 리포트");
            document.addAuthor("Darfin");
            document.open();

            PdfFonts fonts = createFonts();
            addTitle(document, report, fonts);
            addOverview(document, report, content, fonts);
            addAnalysis(document, content.get("analysis"), fonts);
            addMetrics(document, asMap(content.get("metrics")), fonts);

            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI 리포트 PDF 생성 실패", e);
        }
    }

    private void addTitle(Document document, AiReports row, PdfFonts fonts) throws DocumentException {
        Paragraph title = new Paragraph("AI 투자 분석 리포트", fonts.title);
        title.setAlignment(Element.ALIGN_LEFT);
        title.setSpacingAfter(8);
        document.add(title);

        String createdAt = row.getCreatedAt() == null ? "-" : row.getCreatedAt().format(DATE_TIME_FORMATTER);
        Paragraph subtitle = new Paragraph("Report #" + row.getReportId() + " · " + createdAt, fonts.subtitle);
        subtitle.setSpacingAfter(18);
        document.add(subtitle);
    }

    private void addOverview(Document document, AiReports row, Map<String, Object> content, PdfFonts fonts)
            throws DocumentException {
        Map<String, Object> report = asMap(content.get("report"));
        Map<String, Object> health = asMap(report.get("health"));

        PdfPTable table = new PdfPTable(new float[] {1.2f, 2.0f, 1.2f, 2.0f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(18);

        addMetaCell(table, "건강 점수", fonts);
        addValueCell(table, firstText(health.get("total"), row.getHealthScore(), "-"), fonts);
        addMetaCell(table, "투자 성향", fonts);
        addValueCell(table, firstText(report.get("label"), row.getTendencyLabel(), "-"), fonts);
        addMetaCell(table, "리포트 ID", fonts);
        addValueCell(table, String.valueOf(row.getReportId()), fonts);
        addMetaCell(table, "생성일", fonts);
        addValueCell(table, row.getCreatedAt() == null ? "-" : row.getCreatedAt().format(DATE_TIME_FORMATTER), fonts);

        document.add(table);
    }

    private void addAnalysis(Document document, Object analysis, PdfFonts fonts) throws DocumentException {
        addSectionTitle(document, "AI 분석 내용", fonts);
        if (analysis == null) {
            addBodyParagraph(document, "저장된 AI 분석 내용이 없습니다.", fonts);
            return;
        }
        addBodyParagraph(document, readableValue(analysis), fonts);
    }

    private void addMetrics(Document document, Map<String, Object> metrics, PdfFonts fonts) throws DocumentException {
        if (metrics.isEmpty()) return;

        addSectionTitle(document, "주요 지표", fonts);
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);

        flatten(metrics, "").forEach((key, value) -> {
            addMetaCell(table, key, fonts);
            addValueCell(table, value, fonts);
        });

        document.add(table);
    }

    private Map<String, String> flatten(Map<String, Object> source, String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String label = prefix.isBlank() ? key : prefix + "." + key;
            if (value instanceof Map<?, ?>) {
                result.putAll(flatten(asMap(value), label));
            } else {
                result.put(label, readableValue(value));
            }
        });
        return result;
    }

    private void addSectionTitle(Document document, String text, PdfFonts fonts) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, fonts.section);
        paragraph.setSpacingBefore(4);
        paragraph.setSpacingAfter(8);
        document.add(paragraph);
    }

    private void addBodyParagraph(Document document, String text, PdfFonts fonts) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, fonts.body);
        paragraph.setLeading(17);
        paragraph.setSpacingAfter(14);
        document.add(paragraph);
    }

    private void addMetaCell(PdfPTable table, String text, PdfFonts fonts) {
        PdfPCell cell = new PdfPCell(new Phrase(valueOrDefault(text, "-"), fonts.metaLabel));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(new Color(217, 222, 229));
        cell.setBackgroundColor(new Color(245, 247, 250));
        cell.setPadding(7);
        table.addCell(cell);
    }

    private void addValueCell(PdfPTable table, String text, PdfFonts fonts) {
        PdfPCell cell = new PdfPCell(new Phrase(valueOrDefault(text, "-"), fonts.metaValue));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(new Color(217, 222, 229));
        cell.setPadding(7);
        table.addCell(cell);
    }

    private Map<String, Object> parseJsonObject(String value) {
        if (value == null || value.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI 리포트 JSON 파싱 실패", e);
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> result = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String readableValue(Object value) {
        if (value == null) return "-";
        if (value instanceof String) return ((String) value).replace("undefined", "미분류");
        if (value instanceof List<?>) {
            StringBuilder builder = new StringBuilder();
            for (Object item : (List<?>) value) {
                if (builder.length() > 0) builder.append("\n");
                builder.append("- ").append(readableValue(item));
            }
            return builder.toString();
        }
        if (value instanceof Map<?, ?>) {
            StringBuilder builder = new StringBuilder();
            asMap(value).forEach((key, item) -> {
                if (builder.length() > 0) builder.append("\n");
                builder.append(key).append(": ").append(readableValue(item));
            });
            return builder.toString();
        }
        return String.valueOf(value);
    }

    private String firstText(Object first, Object second, String defaultValue) {
        String firstValue = first == null ? null : String.valueOf(first);
        if (hasText(firstValue)) return firstValue;
        String secondValue = second == null ? null : String.valueOf(second);
        return hasText(secondValue) ? secondValue : defaultValue;
    }

    private PdfFonts createFonts() throws IOException, DocumentException {
        BaseFont baseFont = createBaseFont();
        return new PdfFonts(
                new Font(baseFont, 22, Font.BOLD, new Color(27, 36, 48)),
                new Font(baseFont, 12, Font.NORMAL, new Color(78, 88, 104)),
                new Font(baseFont, 14, Font.BOLD, new Color(27, 36, 48)),
                new Font(baseFont, 10, Font.BOLD, new Color(67, 78, 94)),
                new Font(baseFont, 10, Font.NORMAL, new Color(45, 52, 64)),
                new Font(baseFont, 10.5f, Font.NORMAL, new Color(45, 52, 64))
        );
    }

    private BaseFont createBaseFont() throws IOException, DocumentException {
        if (hasText(configuredFontPath) && fontExists(configuredFontPath)) {
            try {
                return BaseFont.createFont(configuredFontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (DocumentException | IOException ignored) {
                // Try system candidates below.
            }
        }

        for (String candidate : FONT_CANDIDATES) {
            if (fontExists(candidate)) {
                try {
                    return BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                } catch (DocumentException | IOException ignored) {
                    // Some OS fonts exist but cannot be embedded by OpenPDF; try the next one.
                }
            }
        }

        return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
    }

    private boolean fontExists(String path) {
        String filePath = path;
        int collectionIndex = path.indexOf(".ttc,");
        if (collectionIndex >= 0) {
            filePath = path.substring(0, collectionIndex + 4);
        }
        return new File(filePath).exists();
    }

    private String valueOrDefault(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static class PdfFonts {
        private final Font title;
        private final Font subtitle;
        private final Font section;
        private final Font metaLabel;
        private final Font metaValue;
        private final Font body;

        private PdfFonts(Font title, Font subtitle, Font section, Font metaLabel,
                         Font metaValue, Font body) {
            this.title = title;
            this.subtitle = subtitle;
            this.section = section;
            this.metaLabel = metaLabel;
            this.metaValue = metaValue;
            this.body = body;
        }
    }
}
