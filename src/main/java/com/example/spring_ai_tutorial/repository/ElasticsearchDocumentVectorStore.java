package com.example.spring_ai_tutorial.repository;

import com.example.spring_ai_tutorial.domain.dto.DocumentSearchResultDto;
import com.example.spring_ai_tutorial.exception.DocumentProcessingException;
import com.example.spring_ai_tutorial.service.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Elasticsearch 기반 벡터 스토어.
 * Spring AI가 자동 구성한 ElasticsearchVectorStore를 VectorStore 인터페이스로 주입받아 사용한다.
 *
 * 업로드 파이프라인:
 *   addDocumentFile → PDF 텍스트 추출(DocumentProcessingService) → 청킹(512 토큰) → ES 인덱싱
 *
 * 질의 파이프라인:
 *   similaritySearch → 쿼리 임베딩 → ES kNN 검색 → DocumentSearchResultDto 반환
 */
@Slf4j
@Repository
public class ElasticsearchDocumentVectorStore {

    private final VectorStore vectorStore;
    private final DocumentProcessingService documentProcessingService;
    private final TokenTextSplitter splitter;

    public ElasticsearchDocumentVectorStore(VectorStore vectorStore,
                                            DocumentProcessingService documentProcessingService) {
        this.vectorStore = vectorStore;
        this.documentProcessingService = documentProcessingService;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(512)
                .withMinChunkSizeChars(350)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
    }

    /**
     * 텍스트를 청킹한 뒤 Elasticsearch에 임베딩 벡터로 저장
     */
    public List<String> addDocument(String id, String fileText, Map<String, Object> metadata) {
        log.debug("문서 추가 시작 - ID: {}, 내용 길이: {}", id, fileText.length());
        try {
            Map<String, Object> combinedMetadata = new HashMap<>(metadata);
            combinedMetadata.put("id", id);

            Document document = new Document(fileText, combinedMetadata);
            List<Document> chunks = splitter.split(document);
            log.debug("청킹 완료 - ID: {}, 청크 수: {}", id, chunks.size());

            vectorStore.add(chunks);
            log.info("ES 인덱싱 완료 - ID: {}", id);

            return chunks.stream().map(Document::getId).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("문서 추가 실패 - ID: {}", id, e);
            throw new DocumentProcessingException("문서 임베딩 및 저장 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 파일(PDF 또는 텍스트)을 처리하여 벡터 스토어에 추가
     */
    public List<String> addDocumentFile(String id, File file, Map<String, Object> metadata) {
        log.debug("파일 처리 시작 - ID: {}, 파일: {}", id, file.getName());
        try {
            String fileText;
            if (file.getName().toLowerCase().endsWith(".pdf")) {
                fileText = documentProcessingService.extractTextFromPdf(file);
            } else {
                fileText = java.nio.file.Files.readString(file.toPath());
            }
            log.debug("텍스트 추출 완료 - 길이: {}", fileText.length());
            return addDocument(id, fileText, metadata);
        } catch (Exception e) {
            log.error("파일 처리 실패 - ID: {}, 파일: {}", id, file.getName(), e);
            throw new DocumentProcessingException("파일 처리 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 텍스트 직접 인덱싱 (about-me 컨텍스트 등 파일 없이 텍스트를 바로 추가할 때 사용)
     */
    public List<String> addText(String id, String text, Map<String, Object> metadata) {
        return addDocument(id, text, metadata);
    }

    /**
     * 지정한 청크 ID 목록을 ES에서 삭제
     */
    public void deleteByIds(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) return;
        try {
            vectorStore.delete(chunkIds);
            log.debug("청크 삭제 완료 - 수: {}", chunkIds.size());
        } catch (Exception e) {
            log.warn("청크 삭제 실패 (무시): {}", e.getMessage());
        }
    }

    /**
     * 질문 벡터와 저장된 청크 벡터의 코사인 유사도를 계산하여 관련 청크 반환
     */
    public List<DocumentSearchResultDto> similaritySearch(String query, int maxResults) {
        log.debug("유사도 검색 시작 - 질의: '{}', 최대 결과: {}", query, maxResults);
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(maxResults)
                    .build();

            List<Document> results = vectorStore.similaritySearch(request);
            if (results == null) results = Collections.emptyList();

            log.debug("유사도 검색 완료 - 결과 수: {}", results.size());

            return results.stream().map(result -> {
                String docId = result.getMetadata().getOrDefault("id", "unknown").toString();
                Map<String, Object> filteredMeta = result.getMetadata().entrySet().stream()
                        .filter(e -> !e.getKey().equals("id"))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                double score = result.getScore() != null ? result.getScore() : 0.0;
                return new DocumentSearchResultDto(docId, result.getText(), filteredMeta, score);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            if (containsIndexNotFound(e)) {
                // 인덱스가 아직 생성되지 않았거나 초기화가 실패한 경우 500 대신 빈 결과를 반환해
                // 상위 계층에서 "관련 정보 없음" 응답으로 자연스럽게 처리되도록 한다.
                log.warn("유사도 검색 대상 인덱스가 없어 빈 결과 반환: {}", e.getMessage());
                return Collections.emptyList();
            }
            log.error("유사도 검색 실패 - 질의: '{}'", query, e);
            throw new DocumentProcessingException("유사도 검색 중 오류 발생: " + e.getMessage(), e);
        }
    }

    private boolean containsIndexNotFound(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("index_not_found_exception")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
