/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** GET /calendar/streak 응답 DTO */
@Builder
@Schema(description = "스트릭 히트맵 응답")
public record StreakResponse(
    @Schema(description = "현재 연속 학습일") int currentStreak,
    @Schema(description = "최장 연속 학습일") int longestStreak,
    @Schema(description = "총 학습일 수") int totalStudyDays,
    @Schema(description = "히트맵 (날짜 → 강도 0~4)") Map<String, Integer> heatmap) {}
