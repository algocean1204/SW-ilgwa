/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.swez.backend.domain.auth.enums.OauthProvider;

/** OauthProvider ↔ DB 소문자 문자열 변환. V1 CHECK 제약('google','kakao')과 일치 */
@Converter
public class OauthProviderConverter implements AttributeConverter<OauthProvider, String> {

  @Override
  public String convertToDatabaseColumn(OauthProvider provider) {
    return provider == null ? null : provider.getDbValue();
  }

  @Override
  public OauthProvider convertToEntityAttribute(String value) {
    return (value == null || value.isBlank()) ? null : OauthProvider.fromDbValue(value);
  }
}
