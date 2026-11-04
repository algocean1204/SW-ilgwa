/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 텔레그램 연동 상태 응답 DTO */
@Builder
@Schema(description = "텔레그램 연동 상태 응답")
public record TelegramStatusResponse(
    @Schema(description = "연동 여부") boolean linked,
    @Schema(description = "텔레그램 Chat ID") Long telegramChatId,
    @Schema(description = "텔레그램 사용자명 (@xxx)") String telegramUsername,
    @Schema(description = "연동 일시") LocalDateTime linkedAt,
    @Schema(description = "마지막 텔레그램 활동 일시") LocalDateTime lastActiveAt) {}
