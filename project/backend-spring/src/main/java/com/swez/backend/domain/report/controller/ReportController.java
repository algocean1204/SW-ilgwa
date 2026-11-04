/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.report.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.swez.backend.domain.report.dto.response.ConceptUnderstandingResponse;
import com.swez.backend.domain.report.dto.response.ProgressReportResponse;
import com.swez.backend.domain.report.dto.response.StudyTimeReportResponse;
import com.swez.backend.domain.report.dto.response.WeeklyReportResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 주간 통계, 과목별 진행률, 학습 시간 추이를 제공하는 학습 리포트 API */
@Tag(name = "Report", description = "학습 리포트 API")
@RequestMapping("/reports")
@SecurityRequirement(name = "bearerAuth")
public interface ReportController {

  @Operation(
      summary = "주간 학습 통계",
      description =
          """
          주간 학습 통계를 조회합니다.

          [**Query Parameter**]
          - week: ISO 주 형식 (예: "2026-W20"), 생략 시 이번 주
          """)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = WeeklyReportResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "잘못된 주 형식 (RPT_001)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @GetMapping("/weekly")
  ResponseEntity<ApiResponse<WeeklyReportResponse>> getWeeklyReport(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "ISO 주 형식 (예: 2026-W20), 생략 시 이번 주") @RequestParam(required = false)
          String week);

  @Operation(summary = "과목별 진행률", description = "사용자의 모든 과외에 대한 과목별 진행률을 조회합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = ProgressReportResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @GetMapping("/progress")
  ResponseEntity<ApiResponse<ProgressReportResponse>> getProgressReport(
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(
      summary = "학습 시간 추이",
      description =
          """
          기간별 학습 시간 추이를 조회합니다.

          [**Query Parameter**]
          - period: 조회 기간 ("7d", "30d", "90d"), 생략 시 "30d"
          """)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = StudyTimeReportResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "잘못된 기간 형식 (RPT_002)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @GetMapping("/study-time")
  ResponseEntity<ApiResponse<StudyTimeReportResponse>> getStudyTimeReport(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "조회 기간 (7d/30d/90d), 생략 시 30d") @RequestParam(required = false)
          String period);

  @Operation(summary = "개념 이해도 조회", description = "특정 코스의 챕터별 퀴즈 정답률을 조회합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = ConceptUnderstandingResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @GetMapping("/concept-understanding")
  ResponseEntity<ApiResponse<ConceptUnderstandingResponse>> getConceptUnderstanding(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "코스 ID", required = true) @RequestParam String courseId);
}
