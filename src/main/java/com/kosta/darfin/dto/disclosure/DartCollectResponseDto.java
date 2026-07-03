package com.kosta.darfin.dto.disclosure;

public class DartCollectResponseDto {

    private boolean success;
    private int savedStockCount;        // stock 테이블에 UPSERT된 기업 수 (0 또는 1)
    private int savedDisclosureCount;   // disclosure 테이블에 UPSERT된 공시 수
    private int skippedCount;           // disclosure_type 미등록으로 건너뛴 공시 수
    private String companyName;
    private String dartCorpCode;
    private String errorMessage;

    public static DartCollectResponseDto ok(String companyName, String dartCorpCode,
                                             int savedStockCount, int savedDisclosureCount,
                                             int skippedCount) {
        DartCollectResponseDto dto = new DartCollectResponseDto();
        dto.success = true;
        dto.companyName = companyName;
        dto.dartCorpCode = dartCorpCode;
        dto.savedStockCount = savedStockCount;
        dto.savedDisclosureCount = savedDisclosureCount;
        dto.skippedCount = skippedCount;
        return dto;
    }

    public static DartCollectResponseDto error(String message) {
        DartCollectResponseDto dto = new DartCollectResponseDto();
        dto.success = false;
        dto.errorMessage = message;
        return dto;
    }

    public boolean isSuccess() { return success; }
    public int getSavedStockCount() { return savedStockCount; }
    public int getSavedDisclosureCount() { return savedDisclosureCount; }
    public int getSkippedCount() { return skippedCount; }
    public String getCompanyName() { return companyName; }
    public String getDartCorpCode() { return dartCorpCode; }
    public String getErrorMessage() { return errorMessage; }
}
