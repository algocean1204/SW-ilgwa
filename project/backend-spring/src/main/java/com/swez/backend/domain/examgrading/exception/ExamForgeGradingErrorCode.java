/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExamForgeGradingErrorCode implements BaseErrorCode {
  ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAMFORGE_GRADING_001", "시험 응시 정보를 찾을 수 없습니다."),
  INVALID_SUBMISSION(HttpStatus.BAD_REQUEST, "EXAMFORGE_GRADING_002", "제출 답안 형식이 올바르지 않습니다."),
  FASTAPI_GRADING_FAILED(
      HttpStatus.BAD_GATEWAY, "EXAMFORGE_GRADING_003", "AI 루브릭 채점 서버 호출에 실패했습니다."),
  SENSITIVE_RESPONSE_BLOCKED(
      HttpStatus.INTERNAL_SERVER_ERROR, "EXAMFORGE_GRADING_004", "민감한 채점 정보가 응답에 포함되어 차단했습니다."),
  /** 응시 소유권 불일치. 요청자가 해당 응시의 소유자가 아닐 때 */
  ATTEMPT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "EXAMFORGE_GRADING_005", "해당 응시에 대한 접근 권한이 없습니다."),
  /** 채점 결과 미존재. 채점이 아직 완료되지 않은 응시에 결과를 조회할 때 */
  GRADE_RESULT_NOT_FOUND(
      HttpStatus.NOT_FOUND, "EXAMFORGE_GRADING_006", "채점 결과를 찾을 수 없습니다. 채점이 완료된 후 조회하세요."),
  /** FastAPI 문항 생성 트리거 호출 실패 */
  FASTAPI_GENERATION_FAILED(
      HttpStatus.BAD_GATEWAY, "EXAMFORGE_GRADING_007", "AI 문항 생성 서버 호출에 실패했습니다."),
  /** 생성 콜백 페이로드의 questions 배열이 비어 있거나 필수 필드가 누락 */
  INVALID_GENERATION_CALLBACK(
      HttpStatus.BAD_REQUEST, "EXAMFORGE_GRADING_008", "문항 생성 콜백 페이로드가 유효하지 않습니다."),
  /** 응시가 이미 생성 완료(READY) 상태이거나 생성 중이 아닐 때 들어온 중복 콜백 */
  ATTEMPT_NOT_IN_GENERATING_STATUS(
      HttpStatus.CONFLICT, "EXAMFORGE_GRADING_009", "응시가 문항 생성 중 상태가 아닙니다."),
  /**
   * 제출 불가 상태. 문항 생성 중(GENERATING)·생성 실패(FAILED)·미시작(CREATED)이거나 정답지 봉인(answer_key_seal)이 없어 채점할 수
   * 없는 응시에 제출이 들어온 경우로, 생성 중 제출과 생성 실패 후 제출을 막는다.
   */
  ATTEMPT_NOT_SUBMITTABLE(
      HttpStatus.CONFLICT, "EXAMFORGE_GRADING_010", "아직 제출할 수 없는 응시 상태입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
