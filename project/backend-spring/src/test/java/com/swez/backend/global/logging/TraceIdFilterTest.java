/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("TraceIdFilter 단위 테스트")
class TraceIdFilterTest {

  private final TraceIdFilter filter = new TraceIdFilter();

  @Test
  @DisplayName("X-Trace-Id 헤더가 있으면 같은 값을 응답 헤더와 MDC에 사용한다")
  void doFilter_existingTraceId_reusesHeaderValue() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.addHeader(TraceId.HEADER_NAME, "trace-test-001");

    FilterChain chain =
        (servletRequest, servletResponse) ->
            assertThat(MDC.get(TraceId.MDC_TRACE_ID)).isEqualTo("trace-test-001");

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(TraceId.HEADER_NAME)).isEqualTo("trace-test-001");
    assertThat(MDC.get(TraceId.MDC_TRACE_ID)).isNull();
  }

  @Test
  @DisplayName("X-Trace-Id 헤더가 없으면 새 TraceId를 생성해 응답 헤더에 내려준다")
  void doFilter_missingTraceId_generatesTraceId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/users/me");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

    assertThat(response.getHeader(TraceId.HEADER_NAME)).isNotBlank();
    assertThat(response.getHeader(TraceId.HEADER_NAME)).hasSize(32);
    assertThat(MDC.get(TraceId.MDC_TRACE_ID)).isNull();
  }
}
