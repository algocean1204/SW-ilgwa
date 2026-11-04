/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.request;

import com.swez.backend.domain.admin.enums.AlertSeverity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 알림 규칙 부분 수정 요청. enabled, severity 중 null인 필드는 변경하지 않는다. */
@Schema(description = "알림 규칙 수정 요청")
public record UpdateAlertRuleRequest(
    @Schema(description = "활성 여부 (null이면 변경하지 않음)") Boolean enabled,
    @Schema(description = "심각도 (null이면 변경하지 않음)") AlertSeverity severity) {}
