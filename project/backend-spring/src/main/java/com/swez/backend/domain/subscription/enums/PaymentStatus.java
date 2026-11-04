/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.enums;

import java.util.Arrays;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 결제 상태. payment.status CHECK 제약과 같다. */
@Getter
@RequiredArgsConstructor
@Schema(description = "결제 상태")
public enum PaymentStatus {
  PAID("paid", "결제 완료"),
  FAILED("failed", "결제 실패"),
  REFUNDED("refunded", "환불");

  /** DB 저장값. payment.status CHECK 제약의 소문자 값과 같다. */
  private final String dbValue;

  private final String description;

  /**
   * DB 소문자 값을 enum 상수로 변환한다.
   *
   * @throws IllegalArgumentException 알 수 없는 값인 경우
   */
  public static PaymentStatus fromDbValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("알 수 없는 결제 상태: " + value);
    }
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("알 수 없는 결제 상태: " + value));
  }
}
