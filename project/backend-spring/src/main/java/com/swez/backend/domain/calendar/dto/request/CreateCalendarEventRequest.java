/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.swez.backend.domain.calendar.enums.CalendarEventType;

import io.swagger.v3.oas.annotations.media.Schema;

/** 캘린더 이벤트 생성 요청 DTO */
@Schema(description = "캘린더 이벤트 생성 요청")
public record CreateCalendarEventRequest(
    @NotBlank @Size(max = 100) @Schema(description = "이벤트 제목", example = "수학 1강 복습") String title,
    @NotNull @Schema(description = "이벤트 유형") CalendarEventType type,
    @NotNull @Schema(description = "날짜", example = "2026-05-01") LocalDate date,
    @Schema(description = "시작 시각 (HH:mm)", example = "14:00") LocalTime scheduledTime,
    @Schema(description = "소요 시간 (분)", example = "60") Integer durationMin,
    @Schema(description = "관련 과외 ID") String courseId,
    @Size(max = 500) @Schema(description = "설명") String description) {}
