/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ExamGrading Cutover 프로퍼티 등록 전용 설정 클래스.
 *
 * <p>{@link ExamGradingCutoverProperties}는 FastAPI 연동 여부와 무관하게 항상 로드되어야 한다. {@link
 * ExamForgeRestClientConfig}가 @ConditionalOnProperty 조건 없이 컨디셔닝될 수 있어 별도 @Configuration으로 분리하여 항상
 * 활성화되도록 한다.
 */
@Configuration
@EnableConfigurationProperties(ExamGradingCutoverProperties.class)
public class ExamGradingCutoverConfig {}
