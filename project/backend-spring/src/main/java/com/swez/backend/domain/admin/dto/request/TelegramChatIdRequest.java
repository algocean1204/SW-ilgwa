/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.request;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 텔레그램 chat_id 수정 요청")
public record TelegramChatIdRequest(
    @Schema(description = "설정할 텔레그램 chat_id. unlink 시 null") Long chatId,
    @NotNull @Schema(description = "작업 유형", example = "set") TelegramChatIdAction action) {}
