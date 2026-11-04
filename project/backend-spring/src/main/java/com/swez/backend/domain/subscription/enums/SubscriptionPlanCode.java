/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.enums;

import java.util.Arrays;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 구독 플랜 코드. subscription_plan.code 자연키와 같다. */
@Getter
@RequiredArgsConstructor
@Schema(description = "구독 플랜 코드")
public enum SubscriptionPlanCode {
  FREE("free", "무료"),
  PRO("pro", "프로"),
  PREMIUM("premium", "프리미엄");

  /** DB 저장값. subscription_plan.code CHECK 제약의 소문자 값과 같다. */
  private final String dbValue;

  private final String description;

  /**
   * DB 소문자 값을 enum 상수로 변환한다.
   *
   * @throws IllegalArgumentException 알 수 없는 값인 경우
   */
  public static SubscriptionPlanCode fromDbValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("알 수 없는 플랜 코드: " + value);
    }
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("알 수 없는 플랜 코드: " + value));
  }
}
