/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ExamGrading Cutover 기능 플래그 프로퍼티.
 *
 * <p>app.examgrading.cutover.enabled=true 이면 MockExamService의 생성·제출·상세조회를 examgrading 신규 lifecycle
 * 서비스로 위임한다. false(기본값)이면 레거시 MCQ 경로를 그대로 사용한다.
 *
 * <p>환경변수 APP_EXAMGRADING_CUTOVER_ENABLED 로 바꿀 수 있어 코드 수정 없이 재시작만으로 전환한다.
 */
@ConfigurationProperties(prefix = "app.examgrading.cutover")
public record ExamGradingCutoverProperties(boolean enabled) {

  /** enabled를 설정하지 않으면 false로 둔다. */
  public ExamGradingCutoverProperties() {
    this(false);
  }
}
