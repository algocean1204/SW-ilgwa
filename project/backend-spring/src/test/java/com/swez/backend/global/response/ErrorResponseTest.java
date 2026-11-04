/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.swez.backend.global.exception.GlobalErrorCode;
import com.swez.backend.global.logging.TraceId;

@DisplayName("ErrorResponse 단위 테스트")
class ErrorResponseTest {

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  @DisplayName("MDC에 traceId가 있으면 에러 응답에 포함한다")
  void of_traceIdExists_includesTraceId() {
    MDC.put(TraceId.MDC_TRACE_ID, "trace-test-001");

    ErrorResponse response = ErrorResponse.of(GlobalErrorCode.SYSTEM_001);

    assertThat(response.getTraceId()).isEqualTo("trace-test-001");
  }

  @Test
  @DisplayName("MDC에 traceId가 없으면 에러 응답 traceId는 null이다")
  void of_traceIdMissing_usesNullTraceId() {
    ErrorResponse response = ErrorResponse.of(GlobalErrorCode.SYSTEM_001);

    assertThat(response.getTraceId()).isNull();
  }
}
