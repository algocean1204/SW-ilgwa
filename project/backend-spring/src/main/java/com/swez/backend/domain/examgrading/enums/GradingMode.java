/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.enums;

/** exam_question_grade.grading_mode 문항 채점 방식. model 패키지의 GradingMode와 값을 맞춘다. */
public enum GradingMode {
  /** 객관식·단답·빈칸·순서·연결형. Spring이 서버 스냅샷으로 확정 채점 */
  DETERMINISTIC,
  /** 서술형·논술형·실기형. FastAPI AI 루브릭 채점 */
  AI_RUBRIC,
  /** AI 신뢰도 미달 또는 채점 오류로 수동 검토 필요 */
  MANUAL_REVIEW_REQUIRED
}
