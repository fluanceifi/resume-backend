package com.example.spring_ai_tutorial.init;

import com.example.spring_ai_tutorial.domain.entity.AppSetting;
import com.example.spring_ai_tutorial.repository.AppSettingRepository;
import com.example.spring_ai_tutorial.repository.ElasticsearchDocumentVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 애플리케이션 시작 시 about-me.txt를 Elasticsearch에 인덱싱한다.
 *
 * 중복 방지 전략:
 *   - MySQL app_settings 테이블에 "about-me-chunk-ids" 키로 청크 ID를 저장.
 *   - 재시작 시 기존 청크를 ES에서 삭제 후 재인덱싱하여 항상 최신 내용을 유지한다.
 *   - 재인덱싱을 원하지 않으면 about-me-indexed 설정을 "skip"으로 변경한다.
 */
@Slf4j
@Component
public class ContextInitializer implements ApplicationRunner {

    private record ContextDoc(String settingKeyIndexed, String settingKeyChunkIds,
                                 String source, String path) {}

    private static final List<ContextDoc> CONTEXT_DOCS = List.of(
            new ContextDoc("about-me-indexed", "about-me-chunk-ids", "about-me", "context/about-me.txt"),
            new ContextDoc("hybrid-rag-indexed", "hybrid-rag-chunk-ids", "hybrid-rag", "context/hybrid-rag.txt"),
            new ContextDoc("smu-club-indexed", "smu-club-chunk-ids", "smu-club", "context/smu-club.txt")
    );

    private final ElasticsearchDocumentVectorStore vectorStore;
    private final AppSettingRepository appSettingRepository;

    public ContextInitializer(ElasticsearchDocumentVectorStore vectorStore,
                              AppSettingRepository appSettingRepository) {
        this.vectorStore = vectorStore;
        this.appSettingRepository = appSettingRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (ContextDoc doc : CONTEXT_DOCS) {
            indexDocument(doc);
        }
    }

    private void indexDocument(ContextDoc doc) {
        var indexedSetting = appSettingRepository.findById(doc.settingKeyIndexed());
        if (indexedSetting.isPresent() && "skip".equals(indexedSetting.get().getValue())) {
            log.info("{} 인덱싱 건너뜀 (설정: skip)", doc.source());
            return;
        }

        log.info("{} 인덱싱 시작...", doc.path());

        // 기존 청크 삭제
        appSettingRepository.findById(doc.settingKeyChunkIds()).ifPresent(s -> {
            String raw = s.getValue();
            if (raw != null && !raw.isBlank()) {
                List<String> oldIds = Arrays.asList(raw.split(","));
                log.debug("기존 {} 청크 삭제 - {} 개", doc.source(), oldIds.size());
                vectorStore.deleteByIds(oldIds);
            }
        });

        // 텍스트 로드
        String text;
        try {
            ClassPathResource resource = new ClassPathResource(doc.path());
            text = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("{} 로드 실패: {}", doc.path(), e.getMessage());
            return;
        }

        // ES 인덱싱
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", doc.source());
        metadata.put("originalFilename", doc.path().substring(doc.path().lastIndexOf('/') + 1));

        try {
            List<String> chunkIds = vectorStore.addText(doc.source() + "-context", text, metadata);
            log.info("{} 인덱싱 완료 - 청크 수: {}", doc.path(), chunkIds.size());

            String chunkIdsValue = String.join(",", chunkIds);
            appSettingRepository.save(new AppSetting(doc.settingKeyChunkIds(), chunkIdsValue));
            appSettingRepository.save(new AppSetting(doc.settingKeyIndexed(), "true"));
        } catch (Exception e) {
            log.warn("{} 초기 인덱싱 실패(서버 기동은 계속): {}", doc.source(), e.getMessage());
        }
    }
}
