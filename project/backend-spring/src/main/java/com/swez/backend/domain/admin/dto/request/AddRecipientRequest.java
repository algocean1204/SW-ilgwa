/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** 텔레그램 알림 수신자 추가 요청 */
@Schema(description = "텔레그램 알림 수신자 추가 요청")
public record AddRecipientRequest(
    @NotBlank @Schema(description = "수신자 닉네임", example = "운영팀장") String nickname,
    @NotNull @Schema(description = "텔레그램 chat_id", example = "123456789") Long chatId) {}
