/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 캘린더 이벤트 유형. DB CHECK 제약과 같은 대문자 값을 쓴다. */
@Getter
@RequiredArgsConstructor
@Schema(description = "캘린더 이벤트 유형")
public enum CalendarEventType {
  LESSON("강의"),
  ASSIGNMENT_DUE("과제 마감"),
  REVIEW("복습"),
  EXAM("시험"),
  CUSTOM("사용자 정의");

  private final String description;
}
