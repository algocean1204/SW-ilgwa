/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 도메인과 무관한 공통 에러 코드. 도메인별 에러는 각 도메인 exception 패키지에 둔다. */
@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements BaseErrorCode {

  // 유효성 검증
  VALIDATION_001(HttpStatus.BAD_REQUEST, "VALIDATION_001", "입력값 유효성 검증에 실패했습니다."),
  VALIDATION_002(HttpStatus.PAYLOAD_TOO_LARGE, "VALIDATION_002", "업로드 파일 크기가 제한을 초과했습니다."),

  // 시스템
  SYSTEM_001(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM_001", "내부 서버 오류가 발생했습니다."),
  SYSTEM_002(HttpStatus.SERVICE_UNAVAILABLE, "SYSTEM_002", "서비스 점검 중입니다."),
  SYSTEM_003(HttpStatus.TOO_MANY_REQUESTS, "SYSTEM_003", "요청 제한을 초과했습니다. 잠시 후 다시 시도해주세요."),
  SYSTEM_004(HttpStatus.METHOD_NOT_ALLOWED, "SYSTEM_004", "지원하지 않는 HTTP 메서드입니다."),
  SYSTEM_005(HttpStatus.NOT_FOUND, "SYSTEM_005", "요청한 경로를 찾을 수 없습니다."),

  // 인증 / 인가. Filter 레이어의 공용 fallback 이고, 도메인 전용 코드는 auth/exception/AuthErrorCode 에 있다.
  AUTH_001(HttpStatus.UNAUTHORIZED, "AUTH_001", "인증이 필요합니다."),
  AUTH_002(HttpStatus.FORBIDDEN, "AUTH_002", "권한이 없습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
