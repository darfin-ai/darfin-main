package com.kosta.darfin.dto.disclosure;

public class DartDocumentResponseDto {

    private boolean success;
    private String text;
    private String errorMessage;

    public static DartDocumentResponseDto ok(String text) {
        DartDocumentResponseDto dto = new DartDocumentResponseDto();
        dto.success = true;
        dto.text = text;
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
    public String getErrorMessage() { return errorMessage; }
}
