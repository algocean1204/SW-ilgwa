/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.swez.backend.domain.mockexam.dto.request.GenerateExamRequest;
import com.swez.backend.domain.mockexam.dto.request.SubmitExamRequest;
import com.swez.backend.domain.mockexam.dto.response.ExamResultResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamDetailResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamListResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** AI 모의고사 생성·조회·제출·결과 API. */
@Tag(name = "Mock Exam", description = "AI 모의고사 API")
@RequestMapping("/tutoring/{courseId}/mock-exams")
@SecurityRequirement(name = "bearerAuth")
public interface MockExamController {

  @Operation(summary = "모의고사 목록 조회", description = "과외에 속한 모의고사 목록을 조회합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = MockExamListResponse.class))),
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
  ResponseEntity<ApiResponse<List<MockExamListResponse>>> getExamList(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId);

  @Operation(
      summary = "AI 모의고사 생성",
      description =
          "생성권(코인)을 1개 차감한 뒤 FastAPI에 모의고사 생성을 비동기 요청합니다."
              + " 즉시 202를 반환하며, 생성이 완료되면 상태가 READY로 변경됩니다."
              + " 완강하지 않은 코스의 첫 번째 응시는 무료이며, 이후부터 생성권 1개가 차감됩니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "202",
        description = "생성 요청 수락",
        content = @Content(schema = @Schema(implementation = MockExamListResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "생성권 부족 (CON_001) 또는 코스 미완강 (CON_004)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CRS_001)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        description = "AI 생성 요청 실패 (MEX_003)",
        content = @Content)
  })
  @PostMapping("/generate")
  ResponseEntity<ApiResponse<MockExamListResponse>> generateExam(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Valid @RequestBody GenerateExamRequest request);

  @Operation(
      summary = "모의고사 문제 조회",
      description = "모의고사 문제를 조회합니다. GENERATING 상태이면 에러를 반환합니다. 정답은 포함하지 않습니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = MockExamDetailResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "아직 생성 중 (MEX_002)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CRS_001) 또는 모의고사 없음 (MEX_001)",
        content = @Content)
  })
  @GetMapping("/{examId}")
  ResponseEntity<ApiResponse<MockExamDetailResponse>> getExamDetail(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "모의고사 ID", required = true) @PathVariable String examId);

  @Operation(summary = "답안 제출", description = "모의고사 답안을 제출하고 채점 결과를 반환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "제출 성공",
        content = @Content(schema = @Schema(implementation = ExamResultResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CRS_001) 또는 모의고사 없음 (MEX_001)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "이미 제출함 (MEX_004)",
        content = @Content)
  })
  @PostMapping("/{examId}/submit")
  ResponseEntity<ApiResponse<ExamResultResponse>> submitExam(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "모의고사 ID", required = true) @PathVariable String examId,
      @Valid @RequestBody SubmitExamRequest request);

  @Operation(summary = "결과 상세 조회", description = "모의고사 제출 결과를 상세 조회합니다. 점수, 문항별 정답/오답, 분석을 포함합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = ExamResultResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CRS_001) 또는 모의고사 없음 (MEX_001) 또는 결과 없음 (MEX_005)",
        content = @Content)
  })
  @GetMapping("/{examId}/results")
  ResponseEntity<ApiResponse<ExamResultResponse>> getExamResult(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "모의고사 ID", required = true) @PathVariable String examId);
}
