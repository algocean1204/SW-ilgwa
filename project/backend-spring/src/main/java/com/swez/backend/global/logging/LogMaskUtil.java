/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.logging;

/** 로그 출력 시 민감값을 마스킹하는 유틸 */
public final class LogMaskUtil {

  private LogMaskUtil() {}

  /** 값의 뒤 4자리만 남기고 앞부분을 ***로 마스킹한다. null이면 "null" 반환 */
  public static String mask(String value) {
    if (value == null) return "null";
    if (value.length() <= 4) return "****";
    return "***" + value.substring(value.length() - 4);
  }
}
