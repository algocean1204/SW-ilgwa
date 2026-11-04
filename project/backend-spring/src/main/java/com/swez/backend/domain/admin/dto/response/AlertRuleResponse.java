/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 알림 규칙 응답 */
@Schema(description = "알림 규칙")
public record AlertRuleResponse(
    String id,
    @Schema(description = "이벤트 유형") String eventType,
    @Schema(description = "표시 이름") String displayName,
    @Schema(description = "활성 여부") boolean enabled,
    @Schema(description = "심각도") String severity,
    @Schema(description = "수정 시각") LocalDateTime updatedAt) {}
