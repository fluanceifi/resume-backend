package com.example.spring_ai_tutorial.service;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * OpenAI의 임베딩 모델을 사용하여 텍스트를 벡터로 변환합니다.
 */
@Service
public class EmbeddingService {

    private final OpenAiEmbeddingModel embeddingModel;

    public EmbeddingService(
            OpenAiApi openAiApi,
            @Value("${spring.ai.openai.embedding.options.model}") String embeddingModelName
    ) {
        this.embeddingModel = new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(embeddingModelName).build(),
                RetryUtils.DEFAULT_RETRY_TEMPLATE
        );
    }

    public OpenAiEmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }
}
