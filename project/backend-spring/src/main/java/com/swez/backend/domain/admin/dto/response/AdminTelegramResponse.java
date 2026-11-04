/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 텔레그램 연동 상태 응답")
public record AdminTelegramResponse(
    @Schema(description = "텔레그램 연동 활성 여부") boolean linked,
    @Schema(description = "텔레그램 chat_id") Long telegramChatId,
    @Schema(description = "연동 시각") LocalDateTime linkedAt) {}
