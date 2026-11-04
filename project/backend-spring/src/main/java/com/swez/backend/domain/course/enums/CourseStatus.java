/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 과외 진행 상태 */
@Getter
@RequiredArgsConstructor
@Schema(description = "과외 진행 상태")
public enum CourseStatus {
  CURRICULUM_GENERATING("커리큘럼 생성 중"),
  CURRICULUM_READY("커리큘럼 생성 완료"),
  /** FastAPI 커리큘럼 생성 파이프라인이 실패했을 때 전이되는 상태 */
  CURRICULUM_FAILED("커리큘럼 생성 실패"),
  ACTIVE("학습 중"),
  COMPLETED("완료"),
  PAUSED("일시정지"),
  DELETED("삭제됨");

  private final String description;
}
