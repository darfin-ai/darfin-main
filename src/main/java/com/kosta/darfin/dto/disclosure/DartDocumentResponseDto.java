package com.kosta.darfin.dto.disclosure;

import java.util.List;

public class DartDocumentResponseDto {

    private boolean success;
    private String text;
    private List<Block> blocks;
    private String errorMessage;

    public static DartDocumentResponseDto ok(String text, List<Block> blocks) {
        DartDocumentResponseDto dto = new DartDocumentResponseDto();
        dto.success = true;
        dto.text = text;
        dto.blocks = blocks;
        return dto;
    }

    public static DartDocumentResponseDto error(String message) {
        DartDocumentResponseDto dto = new DartDocumentResponseDto();
        dto.success = false;
        dto.errorMessage = message;
        return dto;
    }

    public boolean isSuccess() { return success; }
    public String getText() { return text; }
    public List<Block> getBlocks() { return blocks; }
    public String getErrorMessage() { return errorMessage; }

    /**
     * 공시 원문을 문단/표 단위로 구조화한 블록 하나 (Python DocumentBlock과 1:1 대응).
     * charStart/charEnd는 text 안에서 이 블록이 차지하는 문자 구간이며, 기존 하이라이트
     * offset(analysisItems.charOffsetStart/End, termHighlights.startIndex/endIndex)과 좌표계를 공유한다.
     *
     * 표 셀은 문자열이 아니라 Cell(rowSpan/colSpan + Block 목록)이다 — 사업/분기보고서 각주처럼
     * 표 안에 또 표가 중첩된 경우(예: 종속기업 현황 스케줄)를 문자열로 뭉개지 않고 실제 중첩 표로
     * 표현하기 위함이다. 보통은 문단 블록 하나짜리 목록이다.
     */
    public static class Block {
        private String type; // "paragraph" | "table"
        private String text;
        private List<List<Cell>> rows; // row -> cell
        private Integer charStart;
        private Integer charEnd;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public List<List<Cell>> getRows() { return rows; }
        public void setRows(List<List<Cell>> rows) { this.rows = rows; }
        public Integer getCharStart() { return charStart; }
        public void setCharStart(Integer charStart) { this.charStart = charStart; }
        public Integer getCharEnd() { return charEnd; }
        public void setCharEnd(Integer charEnd) { this.charEnd = charEnd; }
    }

    /**
     * 표 셀 하나. rowSpan/colSpan은 DART 원문의 ROWSPAN/COLSPAN 속성값(기본 1)을 그대로
     * 보존한 것으로, 프론트가 &lt;td rowSpan&gt;/&lt;td colSpan&gt;으로 그대로 그려서
     * 재무제표처럼 셀이 여러 행/열에 걸친 표도 원문과 동일한 모양으로 보이게 한다.
     */
    public static class Cell {
        private int rowSpan = 1;
        private int colSpan = 1;
        private List<Block> blocks;

        public int getRowSpan() { return rowSpan; }
        public void setRowSpan(int rowSpan) { this.rowSpan = rowSpan; }
        public int getColSpan() { return colSpan; }
        public void setColSpan(int colSpan) { this.colSpan = colSpan; }
        public List<Block> getBlocks() { return blocks; }
        public void setBlocks(List<Block> blocks) { this.blocks = blocks; }
    }
}
