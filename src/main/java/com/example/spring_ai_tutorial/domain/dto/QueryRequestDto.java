package com.example.spring_ai_tutorial.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 질의 요청 데이터 모델
 */
@Schema(description = "질의 요청 데이터 모델")
public class QueryRequestDto {

    @Schema(description = "사용자 질문", example = "인공지능이란 무엇인가요?")
    private String query;

    @Schema(description = "최대 검색 결과 수", example = "3", defaultValue = "3")
    private int maxResults = 3;

    @Schema(description = "사용할 LLM 모델", example = "gpt-3.5-turbo", defaultValue = "gpt-3.5-turbo")
    private String model = "gpt-3.5-turbo";

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
