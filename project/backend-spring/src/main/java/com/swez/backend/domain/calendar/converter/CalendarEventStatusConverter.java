/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.swez.backend.domain.calendar.enums.CalendarEventStatus;

/** CalendarEventStatus ↔ DB 소문자 문자열 변환. V7 CHECK 제약과 일치 */
@Converter
public class CalendarEventStatusConverter
    implements AttributeConverter<CalendarEventStatus, String> {

  @Override
  public String convertToDatabaseColumn(CalendarEventStatus status) {
    return status == null ? null : status.getDbValue();
  }

  @Override
  public CalendarEventStatus convertToEntityAttribute(String value) {
    return (value == null || value.isBlank()) ? null : CalendarEventStatus.fromDbValue(value);
  }
}
