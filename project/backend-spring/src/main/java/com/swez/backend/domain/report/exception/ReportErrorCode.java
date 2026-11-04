/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.report.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 리포트 도메인 에러 코드 */
@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements BaseErrorCode {
  RPT_001(HttpStatus.BAD_REQUEST, "RPT_001", "잘못된 주 형식입니다."),
  RPT_002(HttpStatus.BAD_REQUEST, "RPT_002", "잘못된 기간 형식입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
