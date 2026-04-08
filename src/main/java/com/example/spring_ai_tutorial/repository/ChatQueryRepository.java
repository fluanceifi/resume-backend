package com.example.spring_ai_tutorial.repository;

import com.example.spring_ai_tutorial.domain.entity.ChatQuery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatQueryRepository extends JpaRepository<ChatQuery, Long> {
}
