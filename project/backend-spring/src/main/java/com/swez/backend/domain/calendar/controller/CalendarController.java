/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.swez.backend.domain.calendar.dto.request.CreateCalendarEventRequest;
import com.swez.backend.domain.calendar.dto.request.UpdateCalendarEventRequest;
import com.swez.backend.domain.calendar.dto.response.CalendarEventResponse;
import com.swez.backend.domain.calendar.dto.response.CalendarResponse;
import com.swez.backend.domain.calendar.dto.response.MissionResponse;
import com.swez.backend.domain.calendar.dto.response.StreakResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 캘린더 API */
@Validated
@Tag(name = "Calendar", description = "학습 캘린더 API")
@RequestMapping("/calendar")
@SecurityRequirement(name = "bearerAuth")
public interface CalendarController {

  @Operation(summary = "월별 캘린더 조회", description = "해당 월의 이벤트·학습일·연속일을 조회합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = CalendarResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @GetMapping
  ResponseEntity<ApiResponse<CalendarResponse>> getCalendar(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "연도", required = true) @RequestParam int year,
      @Parameter(description = "월 (1~12)", required = true) @Min(1) @Max(12) @RequestParam
          int month);

  @Operation(summary = "이벤트 추가", description = "캘린더 이벤트를 수동으로 추가합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "생성 성공",
        content = @Content(schema = @Schema(implementation = CalendarEventResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "유효성 검증 실패",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @PostMapping("/events")
  ResponseEntity<ApiResponse<CalendarEventResponse>> createEvent(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateCalendarEventRequest request);

  @Operation(summary = "이벤트 수정", description = "캘린더 이벤트를 수정합니다. null 필드는 기존 값 유지 (Partial Update).")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "수정 성공",
        content = @Content(schema = @Schema(implementation = CalendarEventResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "이벤트 없음 (CEV_001)",
        content = @Content)
  })
  @PutMapping("/events/{eventId}")
  ResponseEntity<ApiResponse<CalendarEventResponse>> updateEvent(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "이벤트 ID", required = true) @PathVariable String eventId,
      @Valid @RequestBody UpdateCalendarEventRequest request);

  @Operation(summary = "스트릭 히트맵 조회", description = "학습 스트릭 및 히트맵을 조회합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = StreakResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @GetMapping("/streak")
  ResponseEntity<ApiResponse<StreakResponse>> getStreak(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "조회 기간 (개월, 1~12, 기본값 3)")
          @Min(1)
          @Max(12)
          @RequestParam(defaultValue = "3")
          int months);

  @Operation(
      summary = "오늘의 미션 조회",
      description = "오늘의 데일리 미션을 조회합니다. (S2: 하드코딩 3개, S3에서 gamification 연동 예정)")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = MissionResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @GetMapping("/missions/today")
  ResponseEntity<ApiResponse<MissionResponse>> getMissionsToday(
      @AuthenticationPrincipal UserPrincipal principal);
}
