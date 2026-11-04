/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.global.exception.GlobalErrorCode;
import com.swez.backend.global.response.ApiResponse;
import com.swez.backend.global.response.ErrorResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인증되지 않은 요청이 보호 경로에 접근할 때 호출된다. Filter 레이어 예외는 @RestControllerAdvice 가 잡지 못하므로 여기서 직접 ApiResponse
 * 포맷으로 응답을 작성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    log.warn(
        "[JwtAuthenticationEntryPoint] 인증 실패: {} {}", request.getMethod(), request.getRequestURI());

    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    ErrorResponse error = ErrorResponse.of(GlobalErrorCode.AUTH_001);
    objectMapper.writeValue(response.getWriter(), ApiResponse.error(error));
  }
}
