package com.kosta.darfin.dto.disclosure;

public class TermHighlightDto {

    private Long termId;
    private String term;
    private String category;
    private String definition;   // raw_definition 전문
    private int startIndex;
    private int endIndex;

    public TermHighlightDto(Long termId, String term, String category,
                             String definition, int startIndex, int endIndex) {
        this.termId = termId;
        this.term = term;
        this.category = category;
        this.definition = definition;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    public Long getTermId() { return termId; }
    public String getTerm() { return term; }
    public String getCategory() { return category; }
    public String getDefinition() { return definition; }
    public int getStartIndex() { return startIndex; }
    public int getEndIndex() { return endIndex; }
}
