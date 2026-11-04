/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.swez.backend.domain.auth.enums.SubscriptionStatus;

/** SubscriptionStatus ↔ DB 소문자 문자열 변환. V1 CHECK 제약('free','premium','expired')과 일치 */
@Converter
public class SubscriptionStatusConverter implements AttributeConverter<SubscriptionStatus, String> {

  @Override
  public String convertToDatabaseColumn(SubscriptionStatus status) {
    return status == null ? null : status.getDbValue();
  }

  @Override
  public SubscriptionStatus convertToEntityAttribute(String value) {
    return (value == null || value.isBlank()) ? null : SubscriptionStatus.fromDbValue(value);
  }
}
