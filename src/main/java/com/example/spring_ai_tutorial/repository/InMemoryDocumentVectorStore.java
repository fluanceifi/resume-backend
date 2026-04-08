package com.example.spring_ai_tutorial.repository;

import com.example.spring_ai_tutorial.domain.dto.DocumentSearchResultDto;
import com.example.spring_ai_tutorial.exception.DocumentProcessingException;
import com.example.spring_ai_tutorial.service.DocumentProcessingService;
import com.example.spring_ai_tutorial.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * [업로드 Step 4 ~ 5] 텍스트 청킹 후 임베딩하여 인메모리 벡터 스토어에 저장
 * 
 * - Step 4 (청킹): 긴 문서를 그대로(통째로) 임베딩하면 토큰 한도 초과 및 검색 정밀도 저하 문제가 생김
 *   TokenTextSplitter로 512 토큰 단위로 분할해 각 청크가 의미 있는 단위가 되도록 함
 * 
 * - Step 5 (임베딩 + 저장): 각 청크를 OpenAI 임베딩 모델을 통해 벡터화하여 인메모리 벡터스토어에 저장
 *   이후 사용자의 질의 시 질의에 대한 벡터와 코사인 유사도를 계산하여 관련 청크를 빠르게 찾기 위한 사전 작업
 */
@Slf4j
@Repository
public class InMemoryDocumentVectorStore {

    private final DocumentProcessingService documentProcessingService;
    private final VectorStore vectorStore;

    public InMemoryDocumentVectorStore(EmbeddingService embeddingService,
                                       DocumentProcessingService documentProcessingService) {
        this.documentProcessingService = documentProcessingService;
        this.vectorStore = SimpleVectorStore.builder(embeddingService.getEmbeddingModel()).build();
    }

    /**
     * [업로드 Step 4] 텍스트를 청킹한 뒤 [Step 5] 임베딩하여 벡터 스토어에 저장
     */
    public void addDocument(String id, String fileText, Map<String, Object> metadata) {
        log.debug("문서 추가 시작 - ID: {}, 내용 길이: {}", id, fileText.length());
        try {
            Map<String, Object> combinedMetadata = new HashMap<>(metadata);
            combinedMetadata.put("id", id);

            Document document = new Document(fileText, combinedMetadata);

            // [Step 4] 청킹: 임베딩 모델의 토큰 한도를 넘지 않도록, 그리고 검색 정밀도를 높이기 위해
            // 문서를 512 토큰 단위의 청크로 분할(* 최적화를 위한 추가 조정이 필요한 부분, 여기서 512는 임시 설정값)
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(512)
                    .withMinChunkSizeChars(350)
                    .withMinChunkLengthToEmbed(5)
                    .withMaxNumChunks(10000)
                    .withKeepSeparator(true)
                    .build();

            List<Document> chunks = splitter.split(document);
            log.debug("문서 청킹 완료 - ID: {}, 총 청크 수: {}", id, chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                log.debug("청크 [{}/{}] 길이: {} - 내용: {}", i + 1, chunks.size(),
                        chunks.get(i).getText().length(), chunks.get(i).getText());
            }

            // [Step 5] 임베딩 + 저장: 각 청크를 OpenAI 임베딩 모델로 벡터화하여 인메모리 스토어에 저장함
            // 이후 질의 시 질문 벡터와 코사인 유사도를 계산해 관련 청크를 찾는 데 사용됨
            vectorStore.add(chunks);
            log.info("문서 추가 완료 - ID: {}", id);
        } catch (Exception e) {
            log.error("문서 추가 실패 - ID: {}", id, e);
            throw new DocumentProcessingException("문서 임베딩 및 저장 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 파일을 처리하여 벡터 스토어에 추가
     */
    public void addDocumentFile(String id, File file, Map<String, Object> metadata) {
        log.debug("파일 문서 추가 시작 - ID: {}, 파일: {}", id, file.getName());
        try {
            String fileText;
            if (file.getName().toLowerCase().endsWith(".pdf")) {
                fileText = documentProcessingService.extractTextFromPdf(file);
            } else {
                fileText = java.nio.file.Files.readString(file.toPath());
            }
            log.debug("파일 텍스트 추출 완료 - 길이: {}", fileText.length());
            addDocument(id, fileText, metadata);
        } catch (Exception e) {
            log.error("파일 처리 실패 - ID: {}, 파일: {}", id, file.getName(), e);
            throw new DocumentProcessingException("파일 처리 실패: " + e.getMessage(), e);
        }
    }

    /**
     * [질의 Step 3] 질문 벡터와 저장된 청크 벡터들의 코사인 유사도를 계산하여 관련 청크를 반환
     * 
     * 검색 결과 Document를 DocumentSearchResultDto로 변환하며, 출처 메타데이터(파일명 등)도 함께 전달함
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
            log.error("유사도 검색 실패 - 질의: '{}'", query, e);
            throw new DocumentProcessingException("유사도 검색 중 오류 발생: " + e.getMessage(), e);
        }
    }
}
