package com.example.spring_ai_tutorial.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * 문서 검색 결과
 */
@Schema(description = "문서 검색 결과")
public class DocumentSearchResultDto {

    @Schema(description = "문서 ID")
    private final String id;

    @Schema(description = "문서 내용")
    private final String content;

    @Schema(description = "문서 메타데이터")
    private final Map<String, Object> metadata;

    @Schema(description = "유사도 점수")
    private final double score;

    public DocumentSearchResultDto(String id, String content, Map<String, Object> metadata, double score) {
        this.id = id;
        this.content = content;
        this.metadata = metadata;
        this.score = score;
    }

    public String getId() { return id; }
    public String getContent() { return content; }
    public Map<String, Object> getMetadata() { return metadata; }
    public double getScore() { return score; }

    public DocumentResponseDto toDocumentResponseDto() {
        String truncated = content.length() > 500
                ? content.substring(0, 500) + "..."
                : content;
        return new DocumentResponseDto(id, score, truncated, metadata);
    }
}
