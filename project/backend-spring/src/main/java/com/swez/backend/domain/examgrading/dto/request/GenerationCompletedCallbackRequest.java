/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * FastAPI → Spring 생성 완료 콜백 DTO.
 *
 * <p>POST /internal/exam-attempts/{attemptId}/generated 에서 수신한다. X-Internal-Token 인증
 * (InternalTokenFilter).
 *
 * <p>이 DTO는 과제채점 콜백({@code AiResultCallbackRequest})과 별개이며 필드 구조·엔드포인트 모두 분리한다.
 *
 * <h3>generationOutcome 옵셔널 필드</h3>
 *
 * <p>FastAPI가 좁은 소스에서 요청 문항 수를 다 못 채운 경우 0문항 FAILED 대신 부분 출고를 보낼 수 있도록 다음 옵셔널
 * 필드를 둔다. 모두 nullable이고 {@code @NotNull}을 걸지 않아 필드를 보내지 않는 FastAPI도 그대로 동작한다.
 *
 * <ul>
 *   <li>{@code generationOutcome}: passed / partial_success / needs_more_source / failed.
 *       null이면 기존 동작(questions 있으면 정상 출고, 비면 INVALID_GENERATION_CALLBACK).
 *   <li>{@code requestedQuestionCount}: 사용자가 요청한 문항 수 (프론트 "요청 N / 생성 M" 표기용).
 *   <li>{@code actualQuestionCount}: 실제 생성된 문항 수. null이면 questions 길이로 대체한다.
 * </ul>
 */
@Schema(description = "문항 생성 완료 콜백 (FastAPI → Spring 내부 전용)")
public record GenerationCompletedCallbackRequest(
    @NotBlank @Schema(description = "FastAPI 발급 시험 ID", example = "exam_01J...") String examId,
    @NotBlank @Schema(description = "FastAPI 서명 정답지 봉인값 — DB 저장 후 프론트 응답 절대 불가")
        String answerKeySeal,
    // 부분 출고/자료 부족이면 questions가 빈 배열일 수 있어 @NotEmpty를 걸지 않고 서비스에서 outcome과 함께 판단한다
    @Valid @Schema(description = "생성된 문항 목록 — 부분 출고/자료 부족 시 비거나 적을 수 있음")
        List<GenerationCallbackQuestionRequest> questions,
    @Schema(
            description = "생성 결과 — passed/partial_success/needs_more_source/failed (옵셔널, null이면 기존 동작)",
            example = "partial_success")
        String generationOutcome,
    @Schema(description = "요청 문항 수 (옵셔널) — 프론트 '요청 N / 생성 M' 표기용", example = "20")
        Integer requestedQuestionCount,
    @Schema(description = "실제 생성 문항 수 (옵셔널) — null이면 questions 길이로 대체", example = "12")
        Integer actualQuestionCount) {

  /**
   * 레거시 호환 생성자. outcome·요청/생성 문항 수 없이 호출하면 옵셔널 필드를 null로 채워 기존 FastAPI(필드 미전송)
   * 경로와 기존 테스트가 그대로 동작한다.
   */
  public GenerationCompletedCallbackRequest(
      String examId, String answerKeySeal, List<GenerationCallbackQuestionRequest> questions) {
    this(examId, answerKeySeal, questions, null, null, null);
  }
}
