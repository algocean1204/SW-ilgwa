/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 텔레그램 브로드캐스트 발송 결과 */
@Schema(description = "텔레그램 브로드캐스트 발송 결과")
public record BroadcastResponse(
    @Schema(description = "발송 성공 건수") int sentCount,
    @Schema(description = "발송 실패 건수") int failedCount,
    @Schema(description = "발송 실패한 사용자 ID 목록") List<String> failedUserIds) {}
