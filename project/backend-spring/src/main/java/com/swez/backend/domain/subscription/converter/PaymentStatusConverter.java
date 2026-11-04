/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.swez.backend.domain.subscription.enums.PaymentStatus;

/** PaymentStatus ↔ DB 소문자 문자열 변환. V11 CHECK 제약('paid','failed','refunded')과 일치 */
@Converter
public class PaymentStatusConverter implements AttributeConverter<PaymentStatus, String> {

  @Override
  public String convertToDatabaseColumn(PaymentStatus status) {
    return status == null ? null : status.getDbValue();
  }

  @Override
  public PaymentStatus convertToEntityAttribute(String value) {
    return (value == null || value.isBlank()) ? null : PaymentStatus.fromDbValue(value);
  }
}
