/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.request;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TelegramChatIdAction {
  SET,
  UNLINK,
  RELINK;

  @JsonCreator
  public static TelegramChatIdAction from(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Arrays.stream(values())
        .filter(action -> action.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("허용되지 않는 action 입니다: " + value));
  }
}
