/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.examgrading.enums;

/**
 * exam_attempt.failure_reason: 문항 생성이 FAILED로 끝난 사유 구분 코드.
 *
 * <p>AttemptStatus enum에 새 값을 추가하면 DB CHECK constraint·제출 가능 확인·폴링 응답 등 여러 곳을 동시에 손대고
 * 마이그레이션으로 기존 상태값을 재정의해야 한다. 스키마 영향을 최소화하기 위해 상태(FAILED)는 그대로 두고 별도 사유 코드
 * 컬럼으로만 구분한다. 프론트는 status=FAILED + failureReason 조합으로 안내 문구·행동 제안을 분기한다.
 *
 * <p>STRING으로 영속(@Enumerated(STRING)). null이면 사유 미상(레거시/타임아웃 등) FAILED로 본다.
 */
public enum GenerationFailureReason {
  /**
   * 자료 부족. 소스(슬라이드/개념)가 좁아 요청 문항 수만큼 고유 문항을 만들 수 없는 비-에러 실패. 프론트는 "자료가 부족해요"
   * 안내와 자료 추가 업로드 등 행동 제안을 띄운다. 진짜 에러(GENERATION_ERROR)와 구분한다.
   */
  NEEDS_MORE_SOURCE,

  /** 진짜 실패. 0문항, LLM 에러, 타임아웃 등 정상 출고가 불가능한 일반 생성 실패. */
  GENERATION_ERROR
}
