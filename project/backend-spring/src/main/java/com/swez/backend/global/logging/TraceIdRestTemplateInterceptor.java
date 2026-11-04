/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.logging;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

/** 외부 HTTP 호출에 현재 요청의 TraceId를 전파하는 RestTemplate 인터셉터 */
public class TraceIdRestTemplateInterceptor implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    String traceId = MDC.get(TraceId.MDC_TRACE_ID);
    if (StringUtils.hasText(traceId)) {
      request.getHeaders().set(TraceId.HEADER_NAME, traceId);
    }
    return execution.execute(request, body);
  }
}
