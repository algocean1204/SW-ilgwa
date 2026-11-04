/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.swez.backend.domain.auth.enums.OauthProvider;

@DisplayName("OauthProviderConverter 단위 테스트")
class OauthProviderConverterTest {

  private final OauthProviderConverter converter = new OauthProviderConverter();

  @Test
  @DisplayName("GOOGLE → DB 소문자 'google' 저장")
  void convertToDatabaseColumn_GOOGLE_returnsGoogle() {
    assertThat(converter.convertToDatabaseColumn(OauthProvider.GOOGLE)).isEqualTo("google");
  }

  @Test
  @DisplayName("KAKAO → DB 소문자 'kakao' 저장")
  void convertToDatabaseColumn_KAKAO_returnsKakao() {
    assertThat(converter.convertToDatabaseColumn(OauthProvider.KAKAO)).isEqualTo("kakao");
  }

  @Test
  @DisplayName("null 입력 시 null 반환 (null-safe)")
  void convertToDatabaseColumn_null_returnsNull() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
  }

  @Test
  @DisplayName("DB 'google' → GOOGLE enum 반환")
  void convertToEntityAttribute_google_returnsGoogle() {
    assertThat(converter.convertToEntityAttribute("google")).isEqualTo(OauthProvider.GOOGLE);
  }

  @Test
  @DisplayName("DB 'kakao' → KAKAO enum 반환")
  void convertToEntityAttribute_kakao_returnsKakao() {
    assertThat(converter.convertToEntityAttribute("kakao")).isEqualTo(OauthProvider.KAKAO);
  }

  @Test
  @DisplayName("null 값 → null 반환 (null-safe)")
  void convertToEntityAttribute_null_returnsNull() {
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  @DisplayName("blank 값 → null 반환 (null-safe)")
  void convertToEntityAttribute_blank_returnsNull() {
    assertThat(converter.convertToEntityAttribute("")).isNull();
  }

  @Test
  @DisplayName("알 수 없는 DB 값 → IllegalArgumentException")
  void convertToEntityAttribute_unknown_throwsException() {
    assertThatThrownBy(() -> converter.convertToEntityAttribute("NAVER"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("알 수 없는 OAuth 제공자");
  }
}
