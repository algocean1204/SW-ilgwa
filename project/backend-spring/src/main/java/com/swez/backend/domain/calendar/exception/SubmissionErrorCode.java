/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubmissionErrorCode implements BaseErrorCode {
  SBM_001(HttpStatus.NOT_FOUND, "SBM_001", "제출물을 찾을 수 없습니다."),
  SBM_002(HttpStatus.CONFLICT, "SBM_002", "이미 처리된 제출물입니다."),
  SBM_003(HttpStatus.FORBIDDEN, "SBM_003", "내부 인증 토큰이 유효하지 않습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
