package com.example.spring_ai_tutorial.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 문서 처리 관련 커스텀 예외
 */
public class DocumentProcessingException extends ResponseStatusException {

    public DocumentProcessingException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public DocumentProcessingException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
