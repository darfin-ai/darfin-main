package com.kosta.darfin.service.analysis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ports {@code report_classify.py}'s {@code classify_report}. Decides whether a DART
 * filing's {@code report_nm} is one of the four periodic report kinds
 * (사업/반기/분기보고서) and resolves its (reprtCode, bsnsYear).
 */
@Component
public class ReportClassifier {

    private static final Pattern REPORT_NM =
            Pattern.compile("(사업|반기|분기)보고서\\s*\\((\\d{4})\\.(\\d{2})\\)");

    /** Result of classification: (reprtCode, bsnsYear). */
    @Getter
    @RequiredArgsConstructor
    public static class Classified {
        private final String reprtCode;
        private final String bsnsYear;
    }

    public Classified classify(String reportNm) {
        if (reportNm == null) return null;
        Matcher m = REPORT_NM.matcher(reportNm);
        if (!m.find()) return null; // note: Python uses .search(), i.e. find a match anywhere in the string — use find(), not matches()
        String kind = m.group(1);
        String year = m.group(2);
        String month = m.group(3);
        String code;
        switch (kind) {
            case "사업":
                code = "11011";
                break;
            case "반기":
                code = "11012";
                break;
            case "분기":
                if ("03".equals(month)) {
                    code = "11013";
                } else if ("09".equals(month)) {
                    code = "11014";
                } else {
                    code = null;
                }
                break;
            default:
                code = null;
        }
        return code == null ? null : new Classified(code, year);
    }
}
