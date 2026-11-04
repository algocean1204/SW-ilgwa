/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.assignment.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.swez.backend.domain.assignment.dto.request.SubmitAnswerRequest;
import com.swez.backend.domain.assignment.dto.response.AssignmentDetailResponse;
import com.swez.backend.domain.assignment.dto.response.AssignmentListResponse;
import com.swez.backend.domain.assignment.dto.response.GradeResponse;
import com.swez.backend.domain.assignment.dto.response.SubmissionResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 과제 목록·상세 조회, 제출, 채점 결과 조회 API */
@Tag(name = "Assignment", description = "과제 API")
@RequestMapping("/tutoring/{courseId}/assignments")
@SecurityRequirement(name = "bearerAuth")
public interface AssignmentController {

  @Operation(
      summary = "과제 목록 조회",
      description =
          """
      과외 내 과제 목록을 조회합니다.

      [**Query Parameter**]
      - status: 과제 상태 필터 (active / completed). 생략 시 전체 조회.

      마감일 오름차순으로 정렬됩니다.
      """)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = AssignmentListResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CRS_001)",
        content = @Content)
  })
  @GetMapping
  ResponseEntity<ApiResponse<List<AssignmentListResponse>>> getAssignments(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "상태 필터 (active/completed)") @RequestParam(required = false)
          String status);

  @Operation(
      summary = "과제 상세 조회",
      description =
          """
      과제 상세 정보를 조회합니다.
      문제 목록(questions JSON 파싱)과 이전 제출 답안을 포함합니다.
      """)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = AssignmentDetailResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 또는 과제 없음 (CRS_001, ASG_001)",
        content = @Content)
  })
  @GetMapping("/{assignmentId}")
  ResponseEntity<ApiResponse<AssignmentDetailResponse>> getAssignment(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "과제 ID", required = true) @PathVariable String assignmentId);

  @Operation(
      summary = "과제 제출",
      description =
          """
      과제 답안을 제출합니다.
      제출 시 submission 레코드가 status=queued 로 생성됩니다.

      - 마감일이 지난 과제는 제출할 수 없습니다 (400).
      - 이미 제출된 과제는 재제출할 수 없습니다 (409).
      """)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "제출 성공",
        content = @Content(schema = @Schema(implementation = SubmissionResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "마감일 초과 (ASG_002)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 또는 과제 없음 (CRS_001, ASG_001)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "이미 제출됨 (ASG_003)",
        content = @Content)
  })
  @PostMapping("/{assignmentId}/submit")
  ResponseEntity<ApiResponse<SubmissionResponse>> submitAssignment(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "과제 ID", required = true) @PathVariable String assignmentId,
      @Valid @RequestBody SubmitAnswerRequest request);

  @Operation(
      summary = "채점 결과 조회",
      description =
          """
      과제의 채점 결과를 조회합니다.
      status=done 인 제출만 반환됩니다. 미채점 상태이면 404를 반환합니다.
      """)
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = GradeResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외/과제/제출/채점 결과 없음 (CRS_001, ASG_001, ASG_004, ASG_005)",
        content = @Content)
  })
  @GetMapping("/{assignmentId}/grades")
  ResponseEntity<ApiResponse<GradeResponse>> getGrades(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "과제 ID", required = true) @PathVariable String assignmentId);
}
