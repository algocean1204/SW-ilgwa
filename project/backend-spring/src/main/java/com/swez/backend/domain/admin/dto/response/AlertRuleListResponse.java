/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 알림 규칙 목록 응답 */
@Schema(description = "알림 규칙 목록")
public record AlertRuleListResponse(
    @Schema(description = "알림 규칙 목록") List<AlertRuleResponse> rules) {}
