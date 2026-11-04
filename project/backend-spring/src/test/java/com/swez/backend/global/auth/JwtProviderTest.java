/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.swez.backend.global.config.properties.JwtProperties;

@DisplayName("JwtProvider 단위 테스트")
class JwtProviderTest {

  private JwtProvider jwtProvider;
  private JwtProperties properties;

  @BeforeEach
  void setUp() {
    properties = new JwtProperties();
    properties.setSecret("this-is-a-test-secret-key-at-least-32-chars-long");
    properties.setAccessExpiryMs(1_800_000L);
    properties.setRefreshExpiryMs(1_209_600_000L);

    jwtProvider = new JwtProvider(properties);
    // @PostConstruct 는 Spring 컨텍스트 밖에서는 자동 호출되지 않으므로 수동 실행
    jwtProvider.init();
  }

  @Test
  @DisplayName("액세스 토큰 생성 후 검증하면 userId 를 추출할 수 있다")
  void createAccessToken_validate_getUserId() {
    String userId = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
    String token = jwtProvider.createAccessToken(userId);

    assertThat(jwtProvider.validate(token)).isTrue();
    assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
  }

  @Test
  @DisplayName("리프레시 토큰도 동일한 방식으로 검증된다")
  void createRefreshToken_validate() {
    String userId = "usr_01BRZ3NDEKTSV4RRFFQ69G5FAW";
    String token = jwtProvider.createRefreshToken(userId);

    assertThat(jwtProvider.validate(token)).isTrue();
    assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
  }

  @Test
  @DisplayName("변조되거나 빈 토큰은 validate 에서 false 를 반환한다")
  void validate_invalidToken_returnFalse() {
    assertThat(jwtProvider.validate("invalid.jwt.token")).isFalse();
    assertThat(jwtProvider.validate("")).isFalse();
  }

  @Test
  @DisplayName("만료된 토큰은 validate 에서 false 를 반환한다")
  void validate_expiredToken_returnFalse() {
    // 음수 만료 시간으로 생성 즉시 과거가 되는 토큰 발급
    properties.setAccessExpiryMs(-1L);
    JwtProvider expiringProvider = new JwtProvider(properties);
    expiringProvider.init();

    String expiredToken = expiringProvider.createAccessToken("usr_01ARZ3NDEKTSV4RRFFQ69G5FAV");

    assertThat(expiringProvider.validate(expiredToken)).isFalse();
  }
}
