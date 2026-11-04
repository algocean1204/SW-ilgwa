/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 텔레그램 credential 수정 응답")
public record TelegramCredentialUpdateResponse(
    String botTokenFingerprint, LocalDateTime lastRotatedAt, boolean healthCheckPassed) {}
