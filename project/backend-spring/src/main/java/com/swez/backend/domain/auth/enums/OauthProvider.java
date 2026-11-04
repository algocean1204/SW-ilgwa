/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.enums;

import java.util.Arrays;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** OAuth 제공자. Google, Kakao 만 지원하며 dbValue 는 DB CHECK 제약의 소문자 값과 같다. */
@Getter
@RequiredArgsConstructor
@Schema(description = "OAuth 제공자")
public enum OauthProvider {
  GOOGLE("google", "구글"),
  KAKAO("kakao", "카카오");

  /** DB 저장값. V1 마이그레이션 CHECK 제약의 소문자 값과 같다. */
  private final String dbValue;

  private final String description;

  /**
   * DB 소문자 값을 enum 상수로 변환한다.
   *
   * @throws IllegalArgumentException 알 수 없는 값인 경우
   */
  public static OauthProvider fromDbValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("알 수 없는 OAuth 제공자: " + value);
    }
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("알 수 없는 OAuth 제공자: " + value));
  }
}
