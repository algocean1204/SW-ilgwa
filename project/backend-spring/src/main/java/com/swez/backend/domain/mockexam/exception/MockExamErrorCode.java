/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MockExamErrorCode implements BaseErrorCode {
  MEX_001(HttpStatus.NOT_FOUND, "MEX_001", "모의고사를 찾을 수 없습니다."),
  MEX_002(HttpStatus.BAD_REQUEST, "MEX_002", "모의고사가 아직 생성 중입니다."),
  MEX_003(HttpStatus.BAD_GATEWAY, "MEX_003", "AI 모의고사 생성 요청 실패"),
  MEX_004(HttpStatus.CONFLICT, "MEX_004", "이미 제출한 모의고사입니다."),
  MEX_005(HttpStatus.NOT_FOUND, "MEX_005", "제출 결과를 찾을 수 없습니다."),
  MEX_006(HttpStatus.BAD_REQUEST, "MEX_006", "모의고사 문항이 아직 준비되지 않았습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
