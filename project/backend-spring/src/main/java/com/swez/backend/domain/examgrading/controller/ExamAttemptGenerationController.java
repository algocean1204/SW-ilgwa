/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.controller;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.examgrading.dto.request.ExamGenerateRequest;
import com.swez.backend.domain.examgrading.dto.response.AttemptStatusResponse;
import com.swez.backend.domain.examgrading.dto.response.ExamGenerateStartResponse;
import com.swez.backend.domain.examgrading.exception.ExamForgeGradingErrorCode;
import com.swez.backend.domain.examgrading.repository.ExamAttemptRepository;
import com.swez.backend.domain.examgrading.service.ExamAttemptGenerationService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모의고사 문항 생성 응시자용 엔드포인트.
 *
 * <p>모든 엔드포인트는 SecurityConfig의 anyRequest().authenticated() 규칙에 따라 JWT 인증이 자동 요구된다. 소유권 검증은 서비스
 * 레이어에서 한다.
 *
 * <p>현재는 JWT 인증만 확인하고 coin·mockexam 연동 소유권 검증은 하지 않는다.
 */
@Slf4j
@Tag(name = "ExamForge Generation", description = "모의고사 문항 생성 및 상태 조회 API")
@RestController
@RequiredArgsConstructor
// context-path=/api가 /api 프리픽스를 붙이므로 여기서는 뺀다
@RequestMapping("/exam-forge/attempts")
@ConditionalOnProperty(
    prefix = "app.fastapi",
    name = {"base-url", "api-key"})
public class ExamAttemptGenerationController {

  private final ExamAttemptGenerationService generationService;
  private final ExamAttemptRepository attemptRepository;

  @Operation(
      summary = "모의고사 문항 생성 시작",
      description =
          "FastAPI에 비동기 문항 생성을 트리거하고 attempt_id를 즉시 반환한다. "
              + "JWT 인증 필요. "
              + "클라이언트는 반환된 attempt_id로 GET /status 폴링을 수행하며 READY 상태를 기다린다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "202",
        description = "생성 트리거 성공 — attempt_id와 GENERATING 상태 반환"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "JWT 인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        description = "FastAPI 트리거 실패 (EXAMFORGE_GRADING_007)",
        content = @Content)
  })
  @PostMapping("/generate")
  public ResponseEntity<ApiResponse<ExamGenerateStartResponse>> startGeneration(
      @Valid @RequestBody ExamGenerateRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "[ExamAttemptGenerationController] 생성 시작 요청 | userId: {}, subject: {}, questionCount: {}",
        principal.getUserId(),
        request.subject(),
        request.questionCount());
    ExamGenerateStartResponse response =
        generationService.startGeneration(request, principal.getUserId());
    // 비동기 처리가 시작됐음을 202 Accepted로 알린다
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(response, "모의고사 문항 생성이 시작되었습니다. attempt_id로 상태를 폴링하세요."));
  }

  @Operation(
      summary = "응시 상태 조회 (폴링용)",
      description =
          "생성 중(GENERATING)/완료(READY)/실패(FAILED) 등 현재 attempt 상태를 반환한다. "
              + "JWT 인증 필요. 본인 소유 응시만 조회 가능.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "상태 조회 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "소유권 불일치 (EXAMFORGE_GRADING_005)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "attempt 없음 (EXAMFORGE_GRADING_001)",
        content = @Content)
  })
  @GetMapping("/{attemptId}/status")
  public ResponseEntity<ApiResponse<AttemptStatusResponse>> getAttemptStatus(
      @Parameter(description = "응시 식별자", example = "eat_01J...") @PathVariable String attemptId,
      @AuthenticationPrincipal UserPrincipal principal) {
    var attempt =
        attemptRepository
            .findWithUserAndCourseByAttemptId(attemptId)
            .orElseThrow(() -> new CustomException(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));

    // 본인 attempt만 반환한다
    if (attempt.getUser() == null || !principal.getUserId().equals(attempt.getUser().getId())) {
      throw new CustomException(ExamForgeGradingErrorCode.ATTEMPT_ACCESS_DENIED);
    }

    // 프론트는 failureReason과 요청/생성 문항 수로 "자료 부족" 안내와 "요청 N / 생성 M"을 표기한다.
    // 세 값 모두 옵셔널이라 null일 수 있다.
    return ResponseEntity.ok(
        ApiResponse.success(
            new AttemptStatusResponse(
                attemptId,
                attempt.getStatus(),
                attempt.getFailureReason(),
                attempt.getRequestedQuestionCount(),
                attempt.getActualQuestionCount()),
            "응시 상태 조회가 완료되었습니다."));
  }
}
