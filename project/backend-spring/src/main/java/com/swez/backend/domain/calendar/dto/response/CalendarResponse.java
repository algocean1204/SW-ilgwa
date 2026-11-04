/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** GET /calendar 전체 응답 DTO */
@Builder
@Schema(description = "월별 캘린더 응답")
public record CalendarResponse(
    @Schema(description = "연도") int year,
    @Schema(description = "월") int month,
    @Schema(description = "이벤트 목록") List<CalendarEventResponse> events,
    @Schema(description = "학습한 일자 목록 (일 단위)") List<Integer> studyDays,
    @Schema(description = "연속 학습일 수") int streakDays) {}
