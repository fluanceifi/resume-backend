package com.example.spring_ai_tutorial.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 문서 업로드 결과
 */
@Schema(description = "문서 업로드 결과")
public class DocumentUploadResultDto {

    @Schema(description = "생성된 문서 ID")
    private final String documentId;

    @Schema(description = "결과 메시지")
    private final String message;

    public DocumentUploadResultDto(String documentId, String message) {
        this.documentId = documentId;
        this.message = message;
    }

    public String getDocumentId() { return documentId; }
    public String getMessage() { return message; }
}
