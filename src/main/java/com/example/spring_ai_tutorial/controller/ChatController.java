package com.example.spring_ai_tutorial.controller;

import com.example.spring_ai_tutorial.domain.dto.ApiResponseDto;
import com.example.spring_ai_tutorial.domain.dto.ChatRequestDto;
import com.example.spring_ai_tutorial.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Chat API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat API", description = "OpenAI API를 통한 채팅 기능")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "LLM 채팅 메시지 전송",
            description = "사용자의 메시지를 받아 OpenAI API를 통해 응답을 생성합니다.")
    @ApiResponse(responseCode = "200", description = "LLM 응답 성공",
            content = @Content(schema = @Schema(implementation = ApiResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
    @ApiResponse(responseCode = "500", description = "서버 오류")
    @PostMapping("/query")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> sendMessage(
            @Parameter(description = "채팅 요청 객체", required = true)
            @RequestBody ChatRequestDto request) {

        log.info("Chat API 요청 받음: model={}", request.getModel());

        if (request.getQuery() == null || request.getQuery().isBlank()) {
            log.warn("빈 질의가 요청됨");
            return ResponseEntity.badRequest().body(ApiResponseDto.failure("질의가 비어있습니다."));
        }

        try {
            var response = chatService.openAiChat(
                    request.getQuery(),
                    "You are a helpful AI assistant.", // System 프롬프트로 AI에게 역할 부여
                    request.getModel()
            );

            if (response != null) {
                log.debug("LLM 응답 생성: {}", response);
                return ResponseEntity.ok(ApiResponseDto.success(
                        Map.of("answer", response.getResult().getOutput().getText())
                ));
            } else {
                log.error("LLM 응답 생성 실패");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponseDto.failure("LLM 응답 생성 중 오류 발생"));
            }
        } catch (Exception e) {
            log.error("Chat API 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.failure(e.getMessage() != null ? e.getMessage() : "알 수 없는 오류 발생"));
        }
    }
}
