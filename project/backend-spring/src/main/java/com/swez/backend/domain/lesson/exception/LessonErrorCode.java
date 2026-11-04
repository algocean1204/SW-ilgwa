/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LessonErrorCode implements BaseErrorCode {
  LSN_001(HttpStatus.NOT_FOUND, "LSN_001", "슬라이드를 찾을 수 없습니다."),
  LSN_002(HttpStatus.NOT_FOUND, "LSN_002", "퀴즈를 찾을 수 없습니다."),
  LSN_003(HttpStatus.BAD_REQUEST, "LSN_003", "이미 답변한 퀴즈입니다."),
  LSN_004(HttpStatus.NOT_FOUND, "LSN_004", "강의를 찾을 수 없습니다."),
  LSN_005(HttpStatus.FORBIDDEN, "LSN_005", "아직 접근할 수 없는 강의입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
