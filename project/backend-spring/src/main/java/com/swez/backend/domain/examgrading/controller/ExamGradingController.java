/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.swez.backend.domain.examgrading.dto.request.AttemptSubmitRequest;
import com.swez.backend.domain.examgrading.dto.request.ExamSubmissionGradeRequest;
import com.swez.backend.domain.examgrading.dto.response.AttemptDetailResponse;
import com.swez.backend.domain.examgrading.dto.response.AttemptResultResponse;
import com.swez.backend.domain.examgrading.dto.response.ExamSubmissionGradeResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 응시 상세·제출·채점·결과를 다루는 ExamForge 채점 API 인터페이스. */
@Tag(name = "ExamForge Grading", description = "ExamForge 시험 응시 상세·제출·채점·결과 API")
// context-path=/api가 /api 프리픽스를 붙이므로 여기서는 뺀다
@RequestMapping("/exam-forge/submissions")
public interface ExamGradingController {

  @Operation(
      summary = "시험 제출 채점 (직접 채점 API)",
      description =
          "프론트엔드가 제출한 답안을 서버 저장 스냅샷 기준으로 채점한다. "
              + "정답지와 answerKeySeal은 요청/응답에 노출하지 않는다. "
              + "응시 lifecycle을 거치지 않는 직접 채점용 엔드포인트다.")
  @PostMapping("/grade")
  ApiResponse<ExamSubmissionGradeResponse> gradeSubmission(
      @Valid @RequestBody ExamSubmissionGradeRequest request);

  @Operation(
      summary = "응시 상세 조회 — 문항 목록 (응시용)",
      description =
          "응시자의 시험 화면에 표시할 문항 목록을 반환한다. "
              + "정답(correct_answer)·해설(explanation)·출처(source_reference)·채점 메타데이터(grading_metadata)·"
              + "보기의 정답 여부(is_correct)·answerKeySeal은 응답에 포함하지 않는다. "
              + "JWT 인증 필요, 본인 소유 응시만 조회 가능.")
  @GetMapping("/attempts/{attemptId}")
  ApiResponse<AttemptDetailResponse> getAttemptDetail(
      @Parameter(description = "응시 식별자", example = "attempt_20260526_0001") @PathVariable
          String attemptId,
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(
      summary = "응시 제출 + 채점",
      description =
          "응시자가 입력한 답안을 서버에 저장하고 즉시 채점한다. "
              + "1) 답안을 exam_submitted_answer에 저장, "
              + "2) attempt 상태를 SUBMITTED로 전환, "
              + "3) 결정론(객관식) + AI 루브릭(서술형) 채점 수행, "
              + "4) 채점 결과를 exam_grade_result / exam_question_grade에 저장. "
              + "JWT 인증 필요, 본인 소유 응시만 제출 가능.")
  @PostMapping("/attempts/{attemptId}/submit")
  ApiResponse<ExamSubmissionGradeResponse> submitAttempt(
      @Parameter(description = "응시 식별자", example = "attempt_20260526_0001") @PathVariable
          String attemptId,
      @Valid @RequestBody AttemptSubmitRequest request,
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(
      summary = "응시 결과 조회",
      description =
          "채점이 완료된 응시의 결과(총점·합격여부·문항별 채점 결과)를 반환한다. "
              + "정답·해설·출처·채점 메타데이터는 응답에 포함하지 않는다. "
              + "JWT 인증 필요, 본인 소유 응시만 조회 가능. "
              + "채점 전 조회 시 404를 반환한다.")
  @GetMapping("/attempts/{attemptId}/result")
  ApiResponse<AttemptResultResponse> getAttemptResult(
      @Parameter(description = "응시 식별자", example = "attempt_20260526_0001") @PathVariable
          String attemptId,
      @AuthenticationPrincipal UserPrincipal principal);
}
