/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.logging;

/** TraceId 로깅에서 공통으로 사용하는 MDC key와 HTTP 헤더명 */
public final class TraceId {

  public static final String HEADER_NAME = "X-Trace-Id";
  public static final String MDC_TRACE_ID = "traceId";
  public static final String MDC_METHOD = "method";
  public static final String MDC_PATH = "path";
  public static final String MDC_USER_ID = "userId";
  public static final String MDC_ERROR_CODE = "errorCode";

  private TraceId() {}
}
