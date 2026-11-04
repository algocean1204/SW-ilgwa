/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

import com.swez.backend.domain.calendar.enums.CalendarEventStatus;
import com.swez.backend.domain.calendar.enums.CalendarEventType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 캘린더 이벤트 응답 DTO */
@Builder
@Schema(description = "캘린더 이벤트")
public record CalendarEventResponse(
    @Schema(description = "이벤트 ID") String id,
    @Schema(description = "제목") String title,
    @Schema(description = "유형") CalendarEventType type,
    @Schema(description = "날짜") LocalDate date,
    @Schema(description = "상태") CalendarEventStatus status,
    @Schema(description = "시작 시각") LocalTime scheduledTime,
    @Schema(description = "소요 시간 (분)") Integer durationMin,
    @Schema(description = "관련 과외 ID") String courseId) {}
