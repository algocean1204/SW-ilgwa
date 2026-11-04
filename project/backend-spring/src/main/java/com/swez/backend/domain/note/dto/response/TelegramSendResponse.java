/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 텔레그램 노트 전송 응답 DTO */
@Builder
@Schema(description = "텔레그램 전송 응답")
public record TelegramSendResponse(
    @Schema(description = "전송된 노트 ID") String noteId,
    @Schema(description = "전송 성공 여부") boolean sent,
    @Schema(description = "메시지") String message) {}
