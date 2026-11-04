/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Size;

import com.swez.backend.domain.calendar.enums.CalendarEventStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** 캘린더 이벤트 수정 요청 DTO. 부분 수정이라 모든 필드가 nullable 이다. */
@Schema(description = "캘린더 이벤트 수정 요청 (null 필드는 기존 값 유지)")
public record UpdateCalendarEventRequest(
    @Size(max = 100) @Schema(description = "이벤트 제목") String title,
    @Schema(description = "날짜") LocalDate date,
    @Schema(description = "시작 시각") LocalTime scheduledTime,
    @Schema(description = "소요 시간 (분)") Integer durationMin,
    @Schema(description = "상태") CalendarEventStatus status) {}
