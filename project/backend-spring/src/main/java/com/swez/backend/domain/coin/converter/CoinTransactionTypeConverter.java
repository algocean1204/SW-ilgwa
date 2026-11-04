/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.swez.backend.domain.coin.enums.CoinTransactionType;

/**
 * CoinTransactionType과 DB 소문자 문자열을 서로 변환한다. 값은 V12 CHECK 제약
 * ('purchase','use_slide','use_exam','refund')과 맞춘다.
 */
@Converter
public class CoinTransactionTypeConverter
    implements AttributeConverter<CoinTransactionType, String> {

  @Override
  public String convertToDatabaseColumn(CoinTransactionType type) {
    return type == null ? null : type.getDbValue();
  }

  @Override
  public CoinTransactionType convertToEntityAttribute(String value) {
    return (value == null || value.isBlank()) ? null : CoinTransactionType.fromDbValue(value);
  }
}
