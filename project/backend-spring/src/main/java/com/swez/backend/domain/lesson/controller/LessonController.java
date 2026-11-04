/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.swez.backend.domain.lesson.dto.request.ProgressRequest;
import com.swez.backend.domain.lesson.dto.request.QuizAnswerRequest;
import com.swez.backend.domain.lesson.dto.response.QuizListResponse;
import com.swez.backend.domain.lesson.dto.response.QuizResultResponse;
import com.swez.backend.domain.lesson.dto.response.SlideListResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 학습 실행 API. 슬라이드 조회, 진행률 기록, 퀴즈 조회·채점을 제공한다. */
@Validated
@Tag(name = "Lesson Execution", description = "학습 실행 API")
@RequestMapping("/tutoring/{courseId}/lessons/{chapterId}")
@SecurityRequirement(name = "bearerAuth")
public interface LessonController {

  @Operation(summary = "슬라이드 목록 조회", description = "강의(챕터) 내 슬라이드 목록을 조회합니다. 콘텐츠와 오디오 URL을 포함합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = SlideListResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 또는 강의 없음 (CRS_001, LSN_004)",
        content = @Content)
  })
  @GetMapping("/slides")
  ResponseEntity<ApiResponse<SlideListResponse>> getSlides(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId);

  @Operation(summary = "강의 진행률 기록", description = "강의 진행률을 기록합니다. 기존 기록이 없으면 생성, 있으면 갱신합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "기록 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 또는 강의 없음 (CRS_001, LSN_004)",
        content = @Content)
  })
  @PostMapping("/progress")
  ResponseEntity<ApiResponse<Void>> recordProgress(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId,
      @Valid @RequestBody ProgressRequest request);

  @Operation(summary = "퀴즈 목록 조회", description = "강의(챕터) 내 이해도 체크 퀴즈 목록을 조회합니다. 정답은 포함하지 않습니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = QuizListResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 또는 강의 없음 (CRS_001, LSN_004)",
        content = @Content)
  })
  @GetMapping("/quizzes")
  ResponseEntity<ApiResponse<QuizListResponse>> getQuizzes(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId);

  @Operation(
      summary = "퀴즈 기존 답변 조회",
      description = "해당 챕터의 퀴즈에 대한 기존 답변을 조회합니다. 답변이 없으면 data: null을 반환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공 (답변 없으면 data: null)",
        content = @Content(schema = @Schema(implementation = QuizResultResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 또는 강의 없음 (CRS_001, LSN_004)",
        content = @Content)
  })
  @GetMapping("/quiz-answers")
  ResponseEntity<ApiResponse<QuizResultResponse>> getQuizAnswers(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId);

  @Operation(summary = "퀴즈 답변 제출", description = "퀴즈 답변을 제출하고 즉시 채점 결과를 반환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "채점 완료",
        content = @Content(schema = @Schema(implementation = QuizResultResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "이미 답변한 퀴즈 (LSN_003)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외·강의·퀴즈 없음 (CRS_001, LSN_004, LSN_002)",
        content = @Content)
  })
  @PostMapping("/quiz-answers")
  ResponseEntity<ApiResponse<QuizResultResponse>> submitQuizAnswers(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId,
      @Valid @RequestBody QuizAnswerRequest request);
}
