/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.response;

import java.time.LocalDateTime;

import com.swez.backend.domain.auth.enums.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관리자 사용자 정지/해제 응답. 정지 사유와 시각을 포함한다. */
@Schema(description = "관리자 사용자 정지/해제 응답")
public record AdminUserSuspendResponse(
    String id,
    UserStatus status,
    @Schema(description = "정지 사유") String reason,
    @Schema(description = "정지 시각") LocalDateTime suspendedAt) {}
