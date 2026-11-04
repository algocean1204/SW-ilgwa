/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.enums;

/** exam_attempt.status 응시 수명주기 상태 값 */
public enum AttemptStatus {
  /** 응시 생성 완료, 아직 시작 전 */
  CREATED,
  /** FastAPI 문항 비동기 생성 중. generateAsync 호출 직후 전환 */
  GENERATING,
  /** FastAPI 문항 생성 실패. /internal/exam-attempts/{id}/generation-failed 콜백 수신 */
  FAILED,
  /** 응시 진행 중 */
  IN_PROGRESS,
  /** 답안 제출 완료, 채점 대기 */
  SUBMITTED,
  /** 채점 완료 (DETERMINISTIC 전용 또는 AI 루브릭 포함 전체 완료) */
  GRADED,
  /** 수동 검토가 필요한 문항이 남아 있음 */
  MANUAL_REVIEW_REQUIRED,
  /** 문항 생성 완료, 응시 대기. FastAPI generation 콜백 수신 후 READY 상태 */
  READY
}
