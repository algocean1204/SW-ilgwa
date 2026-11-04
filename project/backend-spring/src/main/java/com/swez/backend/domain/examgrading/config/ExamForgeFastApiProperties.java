/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * ExamForge FastAPI 연동 설정 프로퍼티.
 *
 * <p>base-url·api-key는 따로 선언하지 않고 {@code app.fastapi.*} 값을 그대로 쓴다. Docker Compose 환경에서는 {@code
 * APP_FASTAPI_BASE_URL=host.docker.internal:8082}, {@code FASTAPI_API_KEY} 환경변수로 주입한다.
 *
 * <p>타임아웃 기본값:
 *
 * <ul>
 *   <li>connectTimeout: 10초 (기존 fastapiRestTemplate 설정과 동일)
 *   <li>readTimeout: 200초. FastAPI의 LLM 호출 상한 180s가 지난 직후 Spring 소켓을 회수한다.
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "app.fastapi")
public record ExamForgeFastApiProperties(
    @NotBlank String baseUrl,
    @NotBlank String apiKey,
    Duration connectTimeout,
    Duration readTimeout) {

  public ExamForgeFastApiProperties {
    // connectTimeout 미설정 시 기본값 10초 (기존 fastapiRestTemplate와 동일)
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    // readTimeout 미설정 시 기본값 200초 (LLM 호출 상한 180s 직후 Spring 소켓 회수)
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(200);
    }
  }
}
