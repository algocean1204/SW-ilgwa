/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.enums;

import java.util.Arrays;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 구독 상태. subscription.status CHECK 제약과 같다. */
@Getter
@RequiredArgsConstructor
@Schema(description = "구독 상태")
public enum SubscriptionStatusType {
  ACTIVE("active", "활성"),
  CANCELLED("cancelled", "취소"),
  EXPIRED("expired", "만료");

  /** DB 저장값. subscription.status CHECK 제약의 소문자 값과 같다. */
  private final String dbValue;

  private final String description;

  /**
   * DB 소문자 값을 enum 상수로 변환한다.
   *
   * @throws IllegalArgumentException 알 수 없는 값인 경우
   */
  public static SubscriptionStatusType fromDbValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("알 수 없는 구독 상태: " + value);
    }
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("알 수 없는 구독 상태: " + value));
  }
}
