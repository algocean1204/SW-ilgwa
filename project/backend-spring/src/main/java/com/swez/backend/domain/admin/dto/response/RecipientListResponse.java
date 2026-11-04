/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 텔레그램 알림 수신자 목록 응답 */
@Schema(description = "텔레그램 알림 수신자 목록")
public record RecipientListResponse(
    @Schema(description = "수신자 목록") List<RecipientResponse> recipients) {}
