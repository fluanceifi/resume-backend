package com.example.spring_ai_tutorial.controller;

import com.example.spring_ai_tutorial.domain.dto.*;
import com.example.spring_ai_tutorial.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
@Tag(name = "RAG API", description = "Retrieval-Augmented Generation 기능을 위한 API")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @Operation(summary = "PDF 문서 업로드",
            description = "PDF 파일을 업로드하여 벡터 스토어에 저장합니다.")
    @ApiResponse(responseCode = "200", description = "문서 업로드 성공",
            content = @Content(schema = @Schema(implementation = ApiResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
    @ApiResponse(responseCode = "500", description = "서버 오류")
    // [업로드 Step 1] HTTP 요청으로 받은 MultipartFile을 디스크의 임시 파일로 저장한 뒤 RagService에 위임함
    // MultipartFile은 요청 스트림에 묶여 있어 요청 종료 시 사라지므로, 이후 처리에서 안정적으로 읽으려면 실제 파일로 먼저 저장해야 함
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseDto<DocumentUploadResultDto>> uploadDocument(
            @Parameter(description = "업로드할 PDF 파일", required = true)
            @RequestParam("file") MultipartFile file) {

        log.info("문서 업로드 요청 받음: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            log.warn("빈 파일이 업로드됨");
            return ResponseEntity.badRequest().body(ApiResponseDto.failure("파일이 비어있습니다."));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            log.warn("지원하지 않는 파일 형식: {}", filename);
            return ResponseEntity.badRequest().body(ApiResponseDto.failure("PDF 파일만 업로드 가능합니다."));
        }

        File tempFile;
        try {
            // MultipartFile을 디스크의 임시 파일로 복사 — 이후 단계에서 파일을 반복해서 읽을 수 있도록 함
            tempFile = File.createTempFile("upload_", ".pdf");
            file.transferTo(tempFile);
            log.debug("임시 파일 생성됨: {}", tempFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("임시 파일 생성 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.failure("파일 처리 중 오류가 발생했습니다."));
        }

        try {
            String documentId = ragService.uploadPdfFile(tempFile, file.getOriginalFilename());
            log.info("문서 업로드 성공: {}", documentId);
            return ResponseEntity.ok(ApiResponseDto.success(
                    new DocumentUploadResultDto(documentId, "문서가 성공적으로 업로드되었습니다.")
            ));
        } catch (Exception e) {
            log.error("문서 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.failure("문서 처리 중 오류가 발생했습니다: " + e.getMessage()));
        } finally {
            // [업로드 Step 6] 처리 성공/실패 여부와 관계없이 임시 파일을 삭제
            // 예외 발생 시에도 디스크에 파일이 누적되지 않도록 보장하기 위함
            if (tempFile.exists()) {
                tempFile.delete();
                log.debug("임시 파일 삭제됨: {}", tempFile.getAbsolutePath());
            }
        }
    }

    @Operation(summary = "RAG 질의 수행",
            description = "사용자 질문에 대해 관련 문서를 검색하고 RAG 기반 응답을 생성합니다.")
    @ApiResponse(responseCode = "200", description = "질의 성공",
            content = @Content(schema = @Schema(implementation = ApiResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
    @ApiResponse(responseCode = "500", description = "서버 오류")
    // [질의 Step 1] 클라이언트로부터 질문·모델명·최대 결과 수를 받아 유효성을 검사함
    // 빈 질의가 이후 단계까지 흘러가지 않도록 입구에서 차단
    @PostMapping("/query")
    public ResponseEntity<ApiResponseDto<QueryResponseDto>> queryWithRag(
            @Parameter(description = "질의 요청 객체", required = true)
            @RequestBody QueryRequestDto request) {

        log.info("RAG 질의 요청 받음: {}", request.getQuery());

        if (request.getQuery() == null || request.getQuery().isBlank()) {
            log.warn("빈 질의가 요청됨");
            return ResponseEntity.badRequest().body(ApiResponseDto.failure("질의가 비어있습니다."));
        }

        try {
            // [질의 Step 2] 질문과 유사한 문서 청크를 벡터 스토어에서 검색
            // 질문을 임베딩 벡터로 변환한 뒤 저장된 청크 벡터들과 코사인 유사도를 계산해 관련 청크를 반환함
            List<DocumentSearchResultDto> relevantDocs = ragService.retrieve(request.getQuery(), request.getMaxResults());

            // [질의 Step 4 ~ 6] 검색된 청크를 컨텍스트로 조합해 LLM을 호출하고, 답변 + 출처를 반환
            String answer = ragService.generateAnswerWithContexts(request.getQuery(), relevantDocs, request.getModel());

            return ResponseEntity.ok(ApiResponseDto.success(new QueryResponseDto(
                    request.getQuery(),
                    answer,
                    relevantDocs.stream().map(DocumentSearchResultDto::toDocumentResponseDto).collect(Collectors.toList())
            )));
        } catch (Exception e) {
            log.error("RAG 질의 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.failure("질의 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}
