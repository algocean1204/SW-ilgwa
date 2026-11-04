/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.logging;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;

/** 모든 HTTP 요청에 TraceId를 부여하고 요청 완료 로그를 남기는 필터 */
@Slf4j
public class TraceIdFilter extends OncePerRequestFilter {

  private static final int MAX_TRACE_ID_LENGTH = 64;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long startNanos = System.nanoTime();
    String traceId = resolveTraceId(request);
    String method = request.getMethod();
    String path = request.getRequestURI();

    MDC.put(TraceId.MDC_TRACE_ID, traceId);
    MDC.put(TraceId.MDC_METHOD, method);
    MDC.put(TraceId.MDC_PATH, path);
    response.setHeader(TraceId.HEADER_NAME, traceId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
      MDC.put("status", String.valueOf(response.getStatus()));
      MDC.put("elapsedMs", String.valueOf(elapsedMs));
      log.info(
          "HTTP 요청 완료 method={} path={} status={} elapsedMs={} userId={}",
          method,
          path,
          response.getStatus(),
          elapsedMs,
          MDC.get(TraceId.MDC_USER_ID));
      MDC.clear();
    }
  }

  private String resolveTraceId(HttpServletRequest request) {
    String headerTraceId = request.getHeader(TraceId.HEADER_NAME);
    if (StringUtils.hasText(headerTraceId)) {
      String trimmed = headerTraceId.trim();
      if (trimmed.length() <= MAX_TRACE_ID_LENGTH) {
        return trimmed;
      }
    }
    return UUID.randomUUID().toString().replace("-", "");
  }
}
