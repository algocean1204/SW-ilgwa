/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 학습 통계 요약 응답 DTO (GET /users/me/stats) */
@Builder
@Schema(description = "학습 통계 요약")
public record UserStatsResponse(
    @Schema(description = "총 학습 시간 (분)", example = "1240") int totalStudyMinutes,
    @Schema(description = "완료한 강의 수", example = "15") int completedLessons,
    @Schema(description = "연속 학습 일수", example = "7") int streakDays) {}
