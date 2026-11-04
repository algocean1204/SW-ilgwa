/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CalendarErrorCode implements BaseErrorCode {
  CEV_001(HttpStatus.NOT_FOUND, "CEV_001", "캘린더 이벤트를 찾을 수 없습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
