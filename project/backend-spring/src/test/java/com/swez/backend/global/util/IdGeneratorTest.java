/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

@DisplayName("IdGenerator 단위 테스트")
class IdGeneratorTest {

  @Test
  @DisplayName("user() 는 usr_ 프리픽스와 26자 ULID 로 구성된 30자 ID 를 반환한다")
  void user_returnsValidFormat() {
    String id = IdGenerator.user();

    assertThat(id).hasSize(30);
    assertThat(id).matches("^usr_[0-9A-Z]{26}$");
  }

  @Test
  @DisplayName("oauthAccount() 는 oau_ 프리픽스와 26자 ULID 로 구성된 30자 ID 를 반환한다")
  void oauthAccount_returnsValidFormat() {
    String id = IdGenerator.oauthAccount();

    assertThat(id).hasSize(30);
    assertThat(id).matches("^oau_[0-9A-Z]{26}$");
  }

  @Test
  @DisplayName("userSettings() 는 ust_ 프리픽스를 가진다")
  void userSettings_hasCorrectPrefix() {
    assertThat(IdGenerator.userSettings()).startsWith("ust_");
  }

  @Test
  @DisplayName("userPreferences() 는 prf_ 프리픽스를 가진다")
  void userPreferences_hasCorrectPrefix() {
    assertThat(IdGenerator.userPreferences()).startsWith("prf_");
  }

  @Test
  @DisplayName("telegramAccount() 는 tga_ 프리픽스를 가진다")
  void telegramAccount_hasCorrectPrefix() {
    assertThat(IdGenerator.telegramAccount()).startsWith("tga_");
  }

  @RepeatedTest(100)
  @DisplayName("반복 생성 시 모두 고유한 ID 가 반환된다")
  void user_repeatedCall_returnsUniqueIds() {
    String id1 = IdGenerator.user();
    String id2 = IdGenerator.user();

    assertThat(id1).isNotEqualTo(id2);
  }
}
