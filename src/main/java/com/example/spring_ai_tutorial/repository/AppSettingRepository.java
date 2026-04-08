package com.example.spring_ai_tutorial.repository;

import com.example.spring_ai_tutorial.domain.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
