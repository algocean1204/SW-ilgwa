/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.chat.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** AI 채팅 도메인 에러 코드 */
@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {
  CHAT_001(HttpStatus.BAD_GATEWAY, "CHAT_001", "AI 응답 생성에 실패했습니다."),
  CHAT_002(HttpStatus.NOT_FOUND, "CHAT_002", "과외를 찾을 수 없습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
