/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.enums;

import java.util.Arrays;

import com.swez.backend.domain.coin.exception.CoinErrorCode;
import com.swez.backend.global.exception.CustomException;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 구매할 수 있는 코인 팩(번들) 정의 */
@Getter
@RequiredArgsConstructor
public enum CoinBundle {
  COIN_5("COIN_5", "AI 코인 5개", 5, 0, 5, 14900),
  COIN_20("COIN_20", "AI 코인 20개", 20, 2, 22, 49900),
  COIN_60("COIN_60", "AI 코인 60개", 60, 10, 70, 129900);

  private final String bundleId;
  private final String displayName;
  private final int baseAmount;
  private final int bonusAmount;
  private final int totalAmount;
  private final int priceKrw;

  /**
   * 번들 ID 문자열로 enum 상수를 조회한다.
   *
   * @param bundleId 프론트엔드에서 전달하는 번들 ID (예: "COIN_20")
   * @throws CustomException 유효하지 않은 번들 ID인 경우 (CON_007)
   */
  public static CoinBundle fromBundleId(String bundleId) {
    return Arrays.stream(values())
        .filter(b -> b.bundleId.equals(bundleId))
        .findFirst()
        .orElseThrow(() -> new CustomException(CoinErrorCode.CON_007));
  }
}
