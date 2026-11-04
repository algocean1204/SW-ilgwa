/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.swez.backend.domain.calendar.dto.request.AiResultCallbackRequest;
import com.swez.backend.domain.calendar.dto.request.CreateSubmissionRequest;
import com.swez.backend.domain.calendar.dto.response.AiResultAckResponse;
import com.swez.backend.domain.calendar.dto.response.SubmissionResponse;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** FastAPI → Spring 서비스 간 내부 API. JWT 대신 X-Internal-Token 으로 인증한다. */
@Tag(name = "Internal", description = "서비스 간 내부 통신 API (X-Internal-Token 인증)")
@RequestMapping("/internal")
public interface InternalController {

  @Operation(
      summary = "[내부] 제출물 생성",
      description = "FastAPI가 텔레그램 파일 수신 후 Spring에 제출물 row 생성을 요청합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "생성 성공",
        content = @Content(schema = @Schema(implementation = SubmissionResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "토큰 불일치 (SBM_003)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과제 없음 (SBM_001)",
        content = @Content)
  })
  @PostMapping("/submissions")
  ResponseEntity<ApiResponse<SubmissionResponse>> createSubmission(
      @Valid @RequestBody CreateSubmissionRequest request);

  @Operation(
      summary = "[내부] AI 채점 결과 수신",
      description = "FastAPI가 채점 완료 후 Spring에 결과를 전달합니다. 이미 처리된 제출물은 409.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "수신 성공",
        content = @Content(schema = @Schema(implementation = AiResultAckResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "토큰 불일치 (SBM_003)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "제출물 없음 (SBM_001)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "이미 처리된 제출물 (SBM_002)",
        content = @Content)
  })
  @PostMapping("/submissions/{submissionId}/ai-result")
  ResponseEntity<ApiResponse<AiResultAckResponse>> receiveAiResult(
      @Parameter(description = "제출물 ID", required = true) @PathVariable String submissionId,
      @Valid @RequestBody AiResultCallbackRequest request);
}
