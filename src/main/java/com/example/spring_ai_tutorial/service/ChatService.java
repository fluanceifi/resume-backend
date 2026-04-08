package com.example.spring_ai_tutorial.service;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * OpenAI API를 사용하여 질의응답을 수행하는 서비스
 */
@Slf4j
@Service
public class ChatService {

    private final OpenAiApi openAiApi;

    public ChatService(OpenAiApi openAiApi) {
        this.openAiApi = openAiApi;
    }

    /**
     * OpenAI 챗 API를 이용하여 응답을 생성
     */
    public ChatResponse openAiChat(String userInput, String systemMessage, String model) {
        log.debug("OpenAI 챗 호출 시작 - 모델: {}", model);
        try {
            Prompt prompt = new Prompt(
                    List.of(new SystemMessage(systemMessage), new UserMessage(userInput)),
                    ChatOptions.builder().model(model).build()
            );

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .build();

            return chatModel.call(prompt);
        } catch (Exception e) {
            log.error("OpenAI 챗 호출 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
}
