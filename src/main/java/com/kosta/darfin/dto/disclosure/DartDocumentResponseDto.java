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
     */
    public static class Block {
        private String type; // "paragraph" | "table"
        private String text;
        private List<List<String>> rows;
        private Integer charStart;
        private Integer charEnd;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public List<List<String>> getRows() { return rows; }
        public void setRows(List<List<String>> rows) { this.rows = rows; }
        public Integer getCharStart() { return charStart; }
        public void setCharStart(Integer charStart) { this.charStart = charStart; }
        public Integer getCharEnd() { return charEnd; }
        public void setCharEnd(Integer charEnd) { this.charEnd = charEnd; }
    }
}
