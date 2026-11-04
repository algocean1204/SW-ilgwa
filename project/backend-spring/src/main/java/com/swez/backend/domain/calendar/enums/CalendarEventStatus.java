/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.enums;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 캘린더 이벤트 상태. dbValue 는 DB CHECK 제약의 소문자 값과 같다. */
@Getter
@RequiredArgsConstructor
@Schema(description = "캘린더 이벤트 상태")
public enum CalendarEventStatus {
  SCHEDULED("scheduled", "예정"),
  COMPLETED("completed", "완료"),
  CANCELLED("cancelled", "취소"),
  MISSED("missed", "미완료");

  /** DB 저장값. V7 마이그레이션 CHECK 제약의 소문자 값과 같다. */
  private final String dbValue;

  private final String description;

  @JsonValue
  public String getDbValue() {
    return dbValue;
  }

  @JsonCreator
  public static CalendarEventStatus fromDbValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("알 수 없는 캘린더 이벤트 상태: " + value);
    }
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("알 수 없는 캘린더 이벤트 상태: " + value));
  }
}
