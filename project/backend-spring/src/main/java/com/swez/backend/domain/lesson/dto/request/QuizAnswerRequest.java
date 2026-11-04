/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** 퀴즈 답변 제출 요청 DTO (POST /tutoring/{courseId}/lessons/{chapterId}/quiz-answers) */
@Schema(description = "퀴즈 답변 제출 요청")
public record QuizAnswerRequest(
    @Schema(description = "답변 목록") @NotEmpty @Valid List<SingleAnswer> answers) {

  /** 개별 퀴즈 답변 */
  @Schema(description = "개별 퀴즈 답변")
  public record SingleAnswer(
      @Schema(description = "퀴즈 ID", example = "qz_01ARZ3NDEKTSV4RRFFQ69G5FAV") @NotNull String quizId,
      @Schema(description = "선택한 보기 번호 (0부터 시작)", example = "2") @NotNull Integer selectedOption) {}
}
