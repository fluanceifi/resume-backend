package com.example.spring_ai_tutorial.service;

import com.example.spring_ai_tutorial.exception.DocumentProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * [업로드 Step 3] PDF 바이너리에서 순수 텍스트를 추출,
 * 임베딩 모델은 텍스트로 처리하기 때문에, PDF 바이너리를 그대로 넘길 수 없음
 * 
 * DocumentProcessingService는 PDFBox를 사용해 텍스트를 추출한 뒤 이후 단계로 전달 작업을 수행
 */
@Slf4j
@Service
public class DocumentProcessingService {

    /**
     * PDF 파일로부터 텍스트를 추출
     */
    public String extractTextFromPdf(File pdfFile) {
        log.debug("PDF 텍스트 추출 시작: {}", pdfFile.getName());
        try (PDDocument document = PDDocument.load(pdfFile)) {
            log.debug("PDF 문서 로드 성공: {}페이지", document.getNumberOfPages());
            String text = new PDFTextStripper().getText(document);
            log.debug("PDF 텍스트 추출 완료: {} 문자", text.length());
            return text;
        } catch (IOException e) {
            log.error("PDF 텍스트 추출 실패", e);
            throw new DocumentProcessingException("PDF에서 텍스트 추출 실패: " + e.getMessage(), e);
        }
    }
}
