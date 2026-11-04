/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 모의고사 진행 상태 */
@Getter
@RequiredArgsConstructor
@Schema(description = "모의고사 진행 상태")
public enum MockExamStatus {
  GENERATING("생성 중"),
  READY("준비 완료"),
  COMPLETED("완료");

  private final String description;
}
