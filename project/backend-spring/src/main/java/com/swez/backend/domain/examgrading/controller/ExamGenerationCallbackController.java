/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.examgrading.dto.request.GenerationCompletedCallbackRequest;
import com.swez.backend.domain.examgrading.dto.request.GenerationFailedCallbackRequest;
import com.swez.backend.domain.examgrading.service.ExamAttemptGenerationCallbackService;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FastAPI → Spring 내부 통신용 모의고사 문항 생성 콜백 컨트롤러.
 *
 * <p>X-Internal-Token 인증은 {@code InternalTokenFilter}가 /internal/** 경로에서 자동 처리한다.
 *
 * <p>과제채점 콜백({@code /internal/submissions/{id}/ai-result})과 엔드포인트·DTO가 별개이며 {@link
 * ExamAttemptGenerationCallbackService}에만 위임한다.
 */
@Slf4j
@Tag(name = "Internal ExamForge Generation", description = "모의고사 문항 생성 콜백 (X-Internal-Token 인증)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/exam-attempts")
public class ExamGenerationCallbackController {

  private final ExamAttemptGenerationCallbackService callbackService;

  @Operation(
      summary = "[내부] 문항 생성 완료 콜백",
      description =
          "FastAPI가 문항 생성을 완료한 후 Spring에 결과를 전달합니다. "
              + "X-Internal-Token 인증. "
              + "GENERATING 이외 상태에서 수신 시 409 반환(중복 콜백 방지).")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "수신 성공 — attempt READY 전환 완료"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "questions 배열 비어 있음 (EXAMFORGE_GRADING_008)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "X-Internal-Token 불일치",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "attempt 없음 (EXAMFORGE_GRADING_001)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "이미 처리된 콜백 (EXAMFORGE_GRADING_009)",
        content = @Content)
  })
  @PostMapping("/{attemptId}/generated")
  public ResponseEntity<ApiResponse<Void>> receiveGenerationCompleted(
      @Parameter(description = "응시 식별자", required = true) @PathVariable String attemptId,
      @Valid @RequestBody GenerationCompletedCallbackRequest request) {
    log.info(
        "[ExamGenerationCallbackController] 생성 완료 콜백 수신 | attemptId: {}, examId: {}",
        attemptId,
        request.examId());
    callbackService.handleGenerationCompleted(attemptId, request);
    return ResponseEntity.ok(ApiResponse.success(null, "문항 생성 완료 처리에 성공했습니다."));
  }

  @Operation(
      summary = "[내부] 문항 생성 실패 콜백",
      description =
          "FastAPI가 문항 생성에 실패한 후 Spring에 실패 사유를 전달합니다. "
              + "X-Internal-Token 인증. "
              + "attempt 상태를 FAILED로 전환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "수신 성공 — attempt FAILED 전환 완료"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "X-Internal-Token 불일치",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "attempt 없음 (EXAMFORGE_GRADING_001)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "이미 처리된 콜백 (EXAMFORGE_GRADING_009)",
        content = @Content)
  })
  @PostMapping("/{attemptId}/generation-failed")
  public ResponseEntity<ApiResponse<Void>> receiveGenerationFailed(
      @Parameter(description = "응시 식별자", required = true) @PathVariable String attemptId,
      @RequestBody(required = false) GenerationFailedCallbackRequest request) {
    // reason은 선택 필드라 body가 없으면 request가 null로 들어온다
    GenerationFailedCallbackRequest effectiveRequest =
        request != null ? request : new GenerationFailedCallbackRequest(null);
    log.warn(
        "[ExamGenerationCallbackController] 생성 실패 콜백 수신 | attemptId: {}, reason: {}",
        attemptId,
        effectiveRequest.reason());
    callbackService.handleGenerationFailed(attemptId, effectiveRequest);
    return ResponseEntity.ok(ApiResponse.success(null, "문항 생성 실패 처리에 성공했습니다."));
  }
}
