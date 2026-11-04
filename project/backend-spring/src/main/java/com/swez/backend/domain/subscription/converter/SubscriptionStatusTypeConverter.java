/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.swez.backend.domain.subscription.enums.SubscriptionStatusType;

/** SubscriptionStatusType ↔ DB 소문자 문자열 변환. V11 CHECK 제약('active','cancelled','expired')과 일치 */
@Converter
public class SubscriptionStatusTypeConverter
    implements AttributeConverter<SubscriptionStatusType, String> {

  @Override
  public String convertToDatabaseColumn(SubscriptionStatusType status) {
    return status == null ? null : status.getDbValue();
  }

  @Override
  public SubscriptionStatusType convertToEntityAttribute(String value) {
    return (value == null || value.isBlank()) ? null : SubscriptionStatusType.fromDbValue(value);
  }
}
