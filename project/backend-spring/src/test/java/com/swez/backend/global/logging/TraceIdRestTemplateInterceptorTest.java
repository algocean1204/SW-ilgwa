/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpResponse;

@DisplayName("TraceIdRestTemplateInterceptor 단위 테스트")
class TraceIdRestTemplateInterceptorTest {

  private final TraceIdRestTemplateInterceptor interceptor = new TraceIdRestTemplateInterceptor();

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  @DisplayName("MDC에 TraceId가 있으면 외부 요청 헤더에 X-Trace-Id를 추가한다")
  void intercept_traceIdExists_addsHeader() throws IOException {
    MDC.put(TraceId.MDC_TRACE_ID, "trace-test-002");
    MutableHttpRequest request = new MutableHttpRequest();

    interceptor.intercept(request, new byte[0], successExecution());

    assertThat(request.getHeaders().getFirst(TraceId.HEADER_NAME)).isEqualTo("trace-test-002");
  }

  @Test
  @DisplayName("MDC에 TraceId가 없으면 외부 요청 헤더를 추가하지 않는다")
  void intercept_traceIdMissing_doesNotAddHeader() throws IOException {
    MutableHttpRequest request = new MutableHttpRequest();

    interceptor.intercept(request, new byte[0], successExecution());

    assertThat(request.getHeaders().containsKey(TraceId.HEADER_NAME)).isFalse();
  }

  private ClientHttpRequestExecution successExecution() {
    return (request, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);
  }

  private static class MutableHttpRequest implements HttpRequest {
    private final HttpHeaders headers = new HttpHeaders();
    private final Map<String, Object> attributes = new HashMap<>();

    @Override
    public HttpMethod getMethod() {
      return HttpMethod.GET;
    }

    @Override
    public java.net.URI getURI() {
      return java.net.URI.create("https://example.com");
    }

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }

    @Override
    public Map<String, Object> getAttributes() {
      return attributes;
    }
  }
}
