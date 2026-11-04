/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.enums;

/** exam_grade_result.grading_status 응시 전체 채점 완료 상태. model 패키지의 GradingStatus와 값을 맞춘다. */
public enum GradingStatus {
  /** 전체 채점 완료 */
  GRADED,
  /** 수동 검토 대기 중인 문항이 존재함 */
  MANUAL_REVIEW_REQUIRED
}
