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

    private static final String SETTING_KEY_INDEXED  = "about-me-indexed";
    private static final String SETTING_KEY_CHUNK_IDS = "about-me-chunk-ids";
    private static final String CONTEXT_SOURCE        = "about-me";
    private static final String ABOUT_ME_PATH         = "context/about-me.txt";

    private final ElasticsearchDocumentVectorStore vectorStore;
    private final AppSettingRepository appSettingRepository;

    public ContextInitializer(ElasticsearchDocumentVectorStore vectorStore,
                              AppSettingRepository appSettingRepository) {
        this.vectorStore = vectorStore;
        this.appSettingRepository = appSettingRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        // "skip" 값이면 초기화 건너뜀 (운영 중 불필요한 재인덱싱 방지 용도)
        var indexedSetting = appSettingRepository.findById(SETTING_KEY_INDEXED);
        if (indexedSetting.isPresent() && "skip".equals(indexedSetting.get().getValue())) {
            log.info("about-me 인덱싱 건너뜀 (설정: skip)");
            return;
        }

        log.info("about-me.txt 인덱싱 시작...");

        // 기존 청크 삭제
        appSettingRepository.findById(SETTING_KEY_CHUNK_IDS).ifPresent(s -> {
            String raw = s.getValue();
            if (raw != null && !raw.isBlank()) {
                List<String> oldIds = Arrays.asList(raw.split(","));
                log.debug("기존 about-me 청크 삭제 - {} 개", oldIds.size());
                vectorStore.deleteByIds(oldIds);
            }
        });

        // about-me.txt 로드
        String text;
        try {
            ClassPathResource resource = new ClassPathResource(ABOUT_ME_PATH);
            text = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("about-me.txt 로드 실패: {}", e.getMessage());
            return;
        }

        // ES 인덱싱
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", CONTEXT_SOURCE);
        metadata.put("originalFilename", "about-me.txt");

        try {
            List<String> chunkIds = vectorStore.addText("about-me-context", text, metadata);
            log.info("about-me.txt 인덱싱 완료 - 청크 수: {}", chunkIds.size());

            // MySQL에 청크 ID 저장
            String chunkIdsValue = String.join(",", chunkIds);
            appSettingRepository.save(new AppSetting(SETTING_KEY_CHUNK_IDS, chunkIdsValue));
            appSettingRepository.save(new AppSetting(SETTING_KEY_INDEXED, "true"));
        } catch (Exception e) {
            // 외부 AI API(쿼터/네트워크) 이슈로 초기 인덱싱 실패 시에도 서버는 기동되도록 한다.
            log.warn("about-me 초기 인덱싱 실패(서버 기동은 계속): {}", e.getMessage());
        }
    }
}
