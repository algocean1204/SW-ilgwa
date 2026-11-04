/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 텔레그램 알림 수신자 응답 */
@Schema(description = "텔레그램 알림 수신자")
public record RecipientResponse(
    String id,
    @Schema(description = "수신자 닉네임") String nickname,
    @Schema(description = "텔레그램 chat_id") Long chatId,
    @Schema(description = "등록 시각") LocalDateTime createdAt) {}
