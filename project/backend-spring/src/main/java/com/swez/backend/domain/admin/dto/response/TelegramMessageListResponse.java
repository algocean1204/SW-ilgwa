/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 텔레그램 메시지 목록 응답")
public record TelegramMessageListResponse(List<MessageItem> messages) {

  public record MessageItem(
      String id,
      String direction,
      String messageType,
      String text,
      String imageUrl,
      String referenceType,
      String referenceId,
      String telegramMsgId,
      LocalDateTime sentAt) {}
}
