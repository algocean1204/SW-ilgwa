/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 텔레그램 credential 수정 요청")
public record TelegramCredentialUpdateRequest(
    @Schema(description = "새 봇 토큰. write-only") String botToken,
    @Schema(description = "관리자 알림 수신 chat_id") Long adminChatId) {}
