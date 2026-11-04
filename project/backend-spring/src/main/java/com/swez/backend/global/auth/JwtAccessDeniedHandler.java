/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.global.exception.GlobalErrorCode;
import com.swez.backend.global.response.ApiResponse;
import com.swez.backend.global.response.ErrorResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 인증은 됐지만 권한이 부족한 요청에 대해 호출된다. EntryPoint 와 마찬가지로 Filter 레이어이므로 직접 ApiResponse 포맷으로 응답을 작성한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    log.warn("[JwtAccessDeniedHandler] 권한 부족: {} {}", request.getMethod(), request.getRequestURI());

    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    ErrorResponse error = ErrorResponse.of(GlobalErrorCode.AUTH_002);
    objectMapper.writeValue(response.getWriter(), ApiResponse.error(error));
  }
}
