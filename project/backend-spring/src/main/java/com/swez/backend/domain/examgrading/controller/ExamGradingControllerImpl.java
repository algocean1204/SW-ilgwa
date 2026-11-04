/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.controller;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.examgrading.dto.request.AttemptSubmitRequest;
import com.swez.backend.domain.examgrading.dto.request.ExamSubmissionGradeRequest;
import com.swez.backend.domain.examgrading.dto.response.AttemptDetailResponse;
import com.swez.backend.domain.examgrading.dto.response.AttemptResultResponse;
import com.swez.backend.domain.examgrading.dto.response.ExamSubmissionGradeResponse;
import com.swez.backend.domain.examgrading.service.ExamAttemptDetailService;
import com.swez.backend.domain.examgrading.service.ExamAttemptResultService;
import com.swez.backend.domain.examgrading.service.ExamAttemptSubmitService;
import com.swez.backend.domain.examgrading.service.ExamSubmissionGradingService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 응시 상세·제출·채점·결과 엔드포인트를 서비스에 위임한다.
 *
 * <p>모든 엔드포인트는 SecurityConfig의 anyRequest().authenticated() 규칙에 의해 JWT 인증이 자동으로 요구된다 (화이트리스트에 미등록).
 * 소유권 검증(본인 attempt인지)은 각 서비스 레이어에서 한다.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.fastapi",
    name = {"base-url", "api-key"})
public class ExamGradingControllerImpl implements ExamGradingController {

  private final ExamSubmissionGradingService gradingService;
  private final ExamAttemptDetailService detailService;
  private final ExamAttemptSubmitService submitService;
  private final ExamAttemptResultService resultService;

  /** 응시 lifecycle을 거치지 않는 직접 채점 엔드포인트. */
  @Override
  public ApiResponse<ExamSubmissionGradeResponse> gradeSubmission(
      @Valid @RequestBody ExamSubmissionGradeRequest request) {
    return ApiResponse.success(gradingService.gradeSubmission(request), "시험 제출 채점이 완료되었습니다.");
  }

  /** 민감 필드를 뺀 응시 문항 목록을 조회한다. */
  @Override
  public ApiResponse<AttemptDetailResponse> getAttemptDetail(
      @PathVariable String attemptId, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.success(
        detailService.getAttemptDetail(attemptId, principal.getUserId()), "응시 상세 조회가 완료되었습니다.");
  }

  /** 응시를 제출하고 채점한다. 답안 저장 → SUBMITTED 전환 → 채점 → 결과 저장 순서다. */
  @Override
  public ApiResponse<ExamSubmissionGradeResponse> submitAttempt(
      @PathVariable String attemptId,
      @Valid @RequestBody AttemptSubmitRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.success(
        submitService.submitAndGrade(attemptId, request, principal.getUserId()),
        "응시 제출 및 채점이 완료되었습니다.");
  }

  /** 채점 완료 후 집계 결과와 문항별 채점을 조회한다. */
  @Override
  public ApiResponse<AttemptResultResponse> getAttemptResult(
      @PathVariable String attemptId, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.success(
        resultService.getAttemptResult(attemptId, principal.getUserId()), "응시 결과 조회가 완료되었습니다.");
  }
}
