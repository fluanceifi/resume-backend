package com.example.spring_ai_tutorial.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 질의 응답 데이터
 */
@Schema(description = "질의 응답 데이터")
public class QueryResponseDto {

    @Schema(description = "원본 질의")
    private final String query;

    @Schema(description = "생성된 답변")
    private final String answer;

    @Schema(description = "관련 문서 목록")
    private final List<DocumentResponseDto> relevantDocuments;

    public QueryResponseDto(String query, String answer, List<DocumentResponseDto> relevantDocuments) {
        this.query = query;
        this.answer = answer;
        this.relevantDocuments = relevantDocuments;
    }

    public String getQuery() { return query; }
    public String getAnswer() { return answer; }
    public List<DocumentResponseDto> getRelevantDocuments() { return relevantDocuments; }
}
