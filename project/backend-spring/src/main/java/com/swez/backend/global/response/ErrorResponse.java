/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.response;

import java.util.Map;

import org.slf4j.MDC;

import com.swez.backend.global.exception.model.BaseErrorCode;
import com.swez.backend.global.logging.TraceId;

import lombok.Getter;

/** 에러 응답 전용 DTO. ApiResponse.error() 의 error 필드에 담긴다. */
@Getter
public class ErrorResponse {
  private final String code;
  private final String message;

  /** 요청 추적 ID. MDC 에 traceId 가 있으면 담고 없으면 null */
  private final String traceId;

  /** 필드별 유효성 검증 오류 등 추가 정보 (없으면 null) */
  private final Map<String, String> details;

  private ErrorResponse(String code, String message, String traceId, Map<String, String> details) {
    this.code = code;
    this.message = message;
    this.traceId = traceId;
    this.details = details;
  }

  public static ErrorResponse of(BaseErrorCode errorCode) {
    return new ErrorResponse(
        errorCode.getCode(), errorCode.getMessage(), MDC.get(TraceId.MDC_TRACE_ID), null);
  }

  /** 유효성 검증 실패 등 필드별 상세 오류가 있을 때 사용 */
  public static ErrorResponse of(BaseErrorCode errorCode, Map<String, String> details) {
    return new ErrorResponse(
        errorCode.getCode(), errorCode.getMessage(), MDC.get(TraceId.MDC_TRACE_ID), details);
  }
}
