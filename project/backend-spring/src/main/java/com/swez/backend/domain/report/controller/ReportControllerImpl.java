/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.report.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.report.dto.response.ConceptUnderstandingResponse;
import com.swez.backend.domain.report.dto.response.ProgressReportResponse;
import com.swez.backend.domain.report.dto.response.StudyTimeReportResponse;
import com.swez.backend.domain.report.dto.response.WeeklyReportResponse;
import com.swez.backend.domain.report.service.ReportService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 학습 리포트 요청을 ReportService에 위임한다. */
@RestController
@RequiredArgsConstructor
public class ReportControllerImpl implements ReportController {

  private final ReportService reportService;

  @Override
  public ResponseEntity<ApiResponse<WeeklyReportResponse>> getWeeklyReport(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) String week) {
    return ResponseEntity.ok(
        ApiResponse.success(reportService.getWeeklyReport(principal.getUserId(), week)));
  }

  @Override
  public ResponseEntity<ApiResponse<ProgressReportResponse>> getProgressReport(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(reportService.getProgressReport(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<StudyTimeReportResponse>> getStudyTimeReport(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) String period) {
    return ResponseEntity.ok(
        ApiResponse.success(reportService.getStudyTimeReport(principal.getUserId(), period)));
  }

  @Override
  public ResponseEntity<ApiResponse<ConceptUnderstandingResponse>> getConceptUnderstanding(
      @AuthenticationPrincipal UserPrincipal principal, @RequestParam String courseId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            reportService.getConceptUnderstanding(principal.getUserId(), courseId)));
  }
}
