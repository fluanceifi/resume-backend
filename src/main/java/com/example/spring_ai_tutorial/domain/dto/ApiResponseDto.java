package com.example.spring_ai_tutorial.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API 표준 응답 포맷
 */
@Schema(description = "API 표준 응답 포맷")
public class ApiResponseDto<T> {

    @Schema(description = "요청 처리 성공 여부")
    private final boolean success;

    @Schema(description = "응답 데이터 (성공 시)")
    private final T data;

    @Schema(description = "오류 메시지 (실패 시)")
    private final String error;

    private ApiResponseDto(boolean success, T data, String error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponseDto<T> success(T data) {
        return new ApiResponseDto<>(true, data, null);
    }

    public static <T> ApiResponseDto<T> failure(String error) {
        return new ApiResponseDto<>(false, null, error);
    }

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getError() { return error; }
}
