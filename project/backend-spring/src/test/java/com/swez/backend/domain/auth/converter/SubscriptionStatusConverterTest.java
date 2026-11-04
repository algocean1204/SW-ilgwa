/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.swez.backend.domain.auth.enums.SubscriptionStatus;

@DisplayName("SubscriptionStatusConverter 단위 테스트")
class SubscriptionStatusConverterTest {

  private final SubscriptionStatusConverter converter = new SubscriptionStatusConverter();

  @Test
  @DisplayName("FREE → DB 소문자 'free' 저장")
  void convertToDatabaseColumn_FREE_returnsFree() {
    assertThat(converter.convertToDatabaseColumn(SubscriptionStatus.FREE)).isEqualTo("free");
  }

  @Test
  @DisplayName("PREMIUM → DB 소문자 'premium' 저장")
  void convertToDatabaseColumn_PREMIUM_returnsPremium() {
    assertThat(converter.convertToDatabaseColumn(SubscriptionStatus.PREMIUM)).isEqualTo("premium");
  }

  @Test
  @DisplayName("EXPIRED → DB 소문자 'expired' 저장")
  void convertToDatabaseColumn_EXPIRED_returnsExpired() {
    assertThat(converter.convertToDatabaseColumn(SubscriptionStatus.EXPIRED)).isEqualTo("expired");
  }

  @Test
  @DisplayName("null 입력 시 null 반환 (null-safe)")
  void convertToDatabaseColumn_null_returnsNull() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
  }

  @Test
  @DisplayName("DB 'free' → FREE enum 반환")
  void convertToEntityAttribute_free_returnsFree() {
    assertThat(converter.convertToEntityAttribute("free")).isEqualTo(SubscriptionStatus.FREE);
  }

  @Test
  @DisplayName("DB 'premium' → PREMIUM enum 반환")
  void convertToEntityAttribute_premium_returnsPremium() {
    assertThat(converter.convertToEntityAttribute("premium")).isEqualTo(SubscriptionStatus.PREMIUM);
  }

  @Test
  @DisplayName("DB 'expired' → EXPIRED enum 반환")
  void convertToEntityAttribute_expired_returnsExpired() {
    assertThat(converter.convertToEntityAttribute("expired")).isEqualTo(SubscriptionStatus.EXPIRED);
  }

  @Test
  @DisplayName("null 값 → null 반환 (null-safe)")
  void convertToEntityAttribute_null_returnsNull() {
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  @DisplayName("blank 값 → null 반환 (null-safe)")
  void convertToEntityAttribute_blank_returnsNull() {
    assertThat(converter.convertToEntityAttribute("  ")).isNull();
  }

  @Test
  @DisplayName("알 수 없는 DB 값 → IllegalArgumentException")
  void convertToEntityAttribute_unknown_throwsException() {
    assertThatThrownBy(() -> converter.convertToEntityAttribute("INVALID"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("알 수 없는 구독 상태");
  }
}
