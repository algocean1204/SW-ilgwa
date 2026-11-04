/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.calendar.dto.request.CreateCalendarEventRequest;
import com.swez.backend.domain.calendar.dto.request.UpdateCalendarEventRequest;
import com.swez.backend.domain.calendar.dto.response.CalendarEventResponse;
import com.swez.backend.domain.calendar.dto.response.CalendarResponse;
import com.swez.backend.domain.calendar.dto.response.MissionResponse;
import com.swez.backend.domain.calendar.dto.response.StreakResponse;
import com.swez.backend.domain.calendar.service.CalendarService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 캘린더 요청을 CalendarService 에 위임한다. */
@RestController
@RequiredArgsConstructor
public class CalendarControllerImpl implements CalendarController {

  private final CalendarService calendarService;

  @Override
  public ResponseEntity<ApiResponse<CalendarResponse>> getCalendar(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam int year,
      @RequestParam int month) {
    return ResponseEntity.ok(
        ApiResponse.success(calendarService.getCalendar(principal.getUserId(), year, month)));
  }

  @Override
  public ResponseEntity<ApiResponse<CalendarEventResponse>> createEvent(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateCalendarEventRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(calendarService.createEvent(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<CalendarEventResponse>> updateEvent(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String eventId,
      @Valid @RequestBody UpdateCalendarEventRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(calendarService.updateEvent(principal.getUserId(), eventId, request)));
  }

  @Override
  public ResponseEntity<ApiResponse<StreakResponse>> getStreak(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "3") int months) {
    return ResponseEntity.ok(
        ApiResponse.success(calendarService.getStreak(principal.getUserId(), months)));
  }

  @Override
  public ResponseEntity<ApiResponse<MissionResponse>> getMissionsToday(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(calendarService.getMissionsToday(principal.getUserId())));
  }
}
