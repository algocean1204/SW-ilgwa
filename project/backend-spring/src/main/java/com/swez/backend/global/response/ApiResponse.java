/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;

/** 모든 API 응답의 공통 포맷. 정적 팩토리 메서드로만 생성한다. */
@Getter
public class ApiResponse<T> {
  private final boolean success;
  private final T data;

  /** 성공 시 사용자에게 표시할 메시지 (선택) */
  private final String message;

  /** 에러일 때만 직렬화한다. null 이면 응답 필드에서 빠진다. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final ErrorResponse error;

  /** 서버 응답 시각 (ISO 8601, UTC) */
  private final String timestamp;

  private ApiResponse(boolean success, T data, String message, ErrorResponse error) {
    this.success = success;
    this.data = data;
    this.message = message;
    this.error = error;
    this.timestamp = Instant.now().toString();
  }

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null, null);
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(true, data, message, null);
  }

  public static ApiResponse<Void> error(ErrorResponse error) {
    return new ApiResponse<>(false, null, null, error);
  }
}
