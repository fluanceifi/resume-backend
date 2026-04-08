package com.example.spring_ai_tutorial.service;

import com.example.spring_ai_tutorial.domain.entity.ChatQuery;
import com.example.spring_ai_tutorial.repository.ChatQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatQueryService {

    private final ChatQueryRepository chatQueryRepository;

    public ChatQueryService(ChatQueryRepository chatQueryRepository) {
        this.chatQueryRepository = chatQueryRepository;
    }

    public void save(String question, String answer) {
        try {
            chatQueryRepository.save(new ChatQuery(question, answer));
            log.debug("질문 이력 저장 완료: '{}'", question);
        } catch (Exception e) {
            log.warn("질문 이력 저장 실패 (무시): {}", e.getMessage());
        }
    }
}
