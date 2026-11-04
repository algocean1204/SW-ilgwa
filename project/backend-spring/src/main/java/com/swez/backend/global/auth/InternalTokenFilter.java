/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** /internal/** 경로 전용 X-Internal-Token 인증 필터 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

  private static final String INTERNAL_PATH_PREFIX = "/internal/";
  private static final String TOKEN_HEADER = "X-Internal-Token";

  @Value("${app.internal.token}")
  private String internalToken;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getServletPath().startsWith(INTERNAL_PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String token = request.getHeader(TOKEN_HEADER);

    /*
        토큰이 다르면 바로 403 을 반환한다. 타이밍 공격을 막으려고 상수 시간으로 비교한다.
    */
    if (!StringUtils.hasText(token) || !isTokenValid(token)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response
          .getWriter()
          .write(
              "{\"success\":false,\"error\":{\"code\":\"SBM_003\",\"message\":\"내부 인증 토큰이 유효하지 않습니다.\"}}");
      return;
    }

    chain.doFilter(request, response);
  }

  /** 토큰 일치 여부를 상수 시간으로 비교한다. 설정 토큰이 null 이면 false 를 반환한다. */
  private boolean isTokenValid(String token) {
    if (internalToken == null) {
      return false;
    }
    return MessageDigest.isEqual(
        token.getBytes(StandardCharsets.UTF_8), internalToken.getBytes(StandardCharsets.UTF_8));
  }
}
