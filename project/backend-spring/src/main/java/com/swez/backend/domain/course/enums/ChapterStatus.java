/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 강의(챕터) 진행 상태 */
@Getter
@RequiredArgsConstructor
@Schema(description = "강의 진행 상태")
public enum ChapterStatus {
  LOCKED("잠김"),
  AVAILABLE("수강 가능"),
  IN_PROGRESS("수강 중"),
  COMPLETED("완료");

  private final String description;
}
