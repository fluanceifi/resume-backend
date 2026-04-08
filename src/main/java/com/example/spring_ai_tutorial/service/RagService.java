package com.example.spring_ai_tutorial.service;

import com.example.spring_ai_tutorial.domain.dto.DocumentSearchResultDto;
import com.example.spring_ai_tutorial.exception.DocumentProcessingException;
import com.example.spring_ai_tutorial.repository.ElasticsearchDocumentVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG 파이프라인 오케스트레이터.
 *
 * [업로드] HTTP → uploadPdfFile → ElasticsearchDocumentVectorStore → ES 인덱싱
 * [질의]   HTTP → retrieve → ES 유사도 검색 → generateAnswerWithContexts → OpenAI Chat → MySQL 이력 저장
 */
@Slf4j
@Service
public class RagService {

    private final ElasticsearchDocumentVectorStore vectorStore;
    private final ChatService chatService;
    private final ChatQueryService chatQueryService;

    public RagService(ElasticsearchDocumentVectorStore vectorStore,
                      ChatService chatService,
                      ChatQueryService chatQueryService) {
        this.vectorStore = vectorStore;
        this.chatService = chatService;
        this.chatQueryService = chatQueryService;
    }

    /**
     * PDF 파일을 업로드하여 ES 벡터 스토어에 추가
     */
    public String uploadPdfFile(File file, String originalFilename) {
        String documentId = UUID.randomUUID().toString();
        log.info("PDF 문서 업로드 시작. 파일: {}, ID: {}", originalFilename, documentId);

        Map<String, Object> docMetadata = new HashMap<>();
        docMetadata.put("originalFilename", originalFilename != null ? originalFilename : "");
        docMetadata.put("uploadTime", System.currentTimeMillis());

        try {
            vectorStore.addDocumentFile(documentId, file, docMetadata);
            log.info("PDF 문서 업로드 완료. ID: {}", documentId);
            return documentId;
        } catch (Exception e) {
            log.error("문서 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new DocumentProcessingException("문서 처리 중 오류: " + e.getMessage(), e);
        }
    }

    /**
     * 질문과 유사한 문서 청크를 ES에서 검색
     */
    public List<DocumentSearchResultDto> retrieve(String question, int maxResults) {
        log.debug("검색 시작: '{}', 최대 결과 수: {}", question, maxResults);
        return vectorStore.similaritySearch(question, maxResults);
    }

    /**
     * 검색된 청크를 컨텍스트로 LLM 호출 후 응답 생성.
     * 답변 생성 완료 후 질문/답변을 MySQL에 저장한다.
     */
    public String generateAnswerWithContexts(String question, List<DocumentSearchResultDto> relevantDocs, String model) {
        log.debug("RAG 응답 생성 시작: '{}', 모델: {}", question, model);

        if (relevantDocs.isEmpty()) {
            log.info("관련 정보를 찾을 수 없음: '{}'", question);
            String noResultAnswer = "관련 정보를 찾을 수 없습니다. 다른 질문을 시도하거나 관련 문서를 업로드해 주세요.";
            chatQueryService.save(question, noResultAnswer);
            return noResultAnswer;
        }

        // 컨텍스트 문자열 구성
        StringBuilder contextBuf = new StringBuilder();
        for (int i = 0; i < relevantDocs.size(); i++) {
            DocumentSearchResultDto doc = relevantDocs.get(i);
            int num = i + 1;
            if (i > 0) contextBuf.append("\n\n");
            contextBuf.append("[").append(num).append("] ").append(doc.getContent());
        }

        String systemPrompt = """
                당신은 유승준의 포트폴리오 AI 도우미입니다.
                사용자의 질문에 대한 답변을 아래 제공된 정보를 바탕으로 생성해주세요.
                주어진 정보에 답이 없다면 모른다고 솔직히 말해주세요.
                답변 마지막에 사용한 정보의 출처 번호 [1], [2] 등을 반드시 포함해주세요.

                정보:
                """ + contextBuf;

        try {
            var response = chatService.openAiChat(question, systemPrompt, model);
            String aiAnswer = (response != null && response.getResult() != null)
                    ? response.getResult().getOutput().getText()
                    : "응답을 생성할 수 없습니다.";

            // MySQL에 질문/답변 이력 저장
            chatQueryService.save(question, aiAnswer);

            return aiAnswer;
        } catch (Exception e) {
            log.error("AI 모델 호출 중 오류 발생: {}", e.getMessage(), e);
            String fallbackAnswer = "AI 모델 호출 중 오류가 발생했습니다. 검색 결과만 제공합니다:\n\n" +
                    relevantDocs.stream().map(DocumentSearchResultDto::getContent).collect(Collectors.joining("\n\n"));
            chatQueryService.save(question, fallbackAnswer);
            return fallbackAnswer;
        }
    }
}
