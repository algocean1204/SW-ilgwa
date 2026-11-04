/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("InternalTokenFilter 단위 테스트")
class InternalTokenFilterTest {

  private static final String INTERNAL_TOKEN = "internal-test-token";

  private InternalTokenFilter filter;

  @BeforeEach
  void setUp() {
    filter = new InternalTokenFilter();
    ReflectionTestUtils.setField(filter, "internalToken", INTERNAL_TOKEN);
  }

  @Test
  @DisplayName("context-path가 있어도 /internal/** 요청은 토큰 없으면 403으로 차단한다")
  void doFilter_contextPathInternalRequestWithoutToken_forbidden() throws Exception {
    MockHttpServletRequest request = internalRequestWithContextPath();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, failIfReached());

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("context-path가 있어도 /internal/** 요청은 틀린 토큰이면 403으로 차단한다")
  void doFilter_contextPathInternalRequestWithWrongToken_forbidden() throws Exception {
    MockHttpServletRequest request = internalRequestWithContextPath();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.addHeader("X-Internal-Token", "wrong-token");

    filter.doFilter(request, response, failIfReached());

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("context-path가 있어도 /internal/** 요청은 올바른 토큰이면 통과한다")
  void doFilter_contextPathInternalRequestWithCorrectToken_passes() throws Exception {
    MockHttpServletRequest request = internalRequestWithContextPath();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.addHeader("X-Internal-Token", INTERNAL_TOKEN);

    filter.doFilter(
        request, response, (servletRequest, servletResponse) -> response.setStatus(204));

    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  @DisplayName("설정 토큰과 앞부분만 일치하는 토큰은 403으로 차단한다")
  void doFilter_prefixMatchingToken_forbidden() throws Exception {
    MockHttpServletRequest request = internalRequestWithContextPath();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.addHeader("X-Internal-Token", INTERNAL_TOKEN + "-suffix");

    filter.doFilter(request, response, failIfReached());

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("설정 토큰이 null이어도 NPE 없이 403으로 차단한다")
  void doFilter_nullConfiguredToken_forbidden() throws Exception {
    ReflectionTestUtils.setField(filter, "internalToken", null);
    MockHttpServletRequest request = internalRequestWithContextPath();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.addHeader("X-Internal-Token", INTERNAL_TOKEN);

    filter.doFilter(request, response, failIfReached());

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("/internal/** 외 요청은 내부 토큰 없이도 필터를 건너뛴다")
  void doFilter_nonInternalRequest_skipsInternalTokenCheck() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setContextPath("/api");
    request.setServletPath("/health");

    filter.doFilter(
        request, response, (servletRequest, servletResponse) -> response.setStatus(204));

    assertThat(response.getStatus()).isEqualTo(204);
  }

  private MockHttpServletRequest internalRequestWithContextPath() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/internal/submissions/sub_01/ai-result");
    request.setContextPath("/api");
    request.setServletPath("/internal/submissions/sub_01/ai-result");
    return request;
  }

  private FilterChain failIfReached() {
    return (servletRequest, servletResponse) -> {
      throw new AssertionError("토큰 검증 실패 요청은 다음 필터로 넘어가면 안 된다");
    };
  }
}
