/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 다운그레이드 예약 응답 DTO */
@Builder
@Schema(description = "다운그레이드 예약 응답")
public record DowngradeResponse(
    @Schema(description = "예약된 플랜 코드") String scheduledPlanCode,
    @Schema(description = "전환 적용일") LocalDateTime effectiveFrom,
    @Schema(description = "현재 플랜 유지 기한") LocalDateTime currentPlanActiveUntil) {}
