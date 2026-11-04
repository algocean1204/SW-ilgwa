/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.enums;

import java.util.Arrays;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 코인 거래 유형. coin_transaction.type CHECK 제약과 값을 맞춘다. */
@Getter
@RequiredArgsConstructor
@Schema(description = "코인 거래 유형")
public enum CoinTransactionType {
  PURCHASE("purchase", "구매"),
  USE_SLIDE("use_slide", "슬라이드 사용"),
  USE_EXAM("use_exam", "모의고사 사용"),
  REFUND("refund", "환불"),
  SUBSCRIPTION_GRANT("subscription_grant", "구독 코인 지급");

  /** DB 저장값. coin_transaction.type CHECK 제약의 소문자 값과 같다. */
  private final String dbValue;

  private final String description;

  /**
   * DB 소문자 값을 enum 상수로 변환한다.
   *
   * @throws IllegalArgumentException 알 수 없는 값인 경우
   */
  public static CoinTransactionType fromDbValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("알 수 없는 코인 거래 유형: " + value);
    }
    return Arrays.stream(values())
        .filter(v -> v.dbValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("알 수 없는 코인 거래 유형: " + value));
  }
}
