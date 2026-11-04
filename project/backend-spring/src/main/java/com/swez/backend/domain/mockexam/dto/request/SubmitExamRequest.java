/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import io.swagger.v3.oas.annotations.media.Schema;

/** 모의고사 답안 제출 요청 DTO (POST /tutoring/{courseId}/mock-exams/{examId}/submit) */
@Schema(description = "모의고사 답안 제출 요청")
public record SubmitExamRequest(
    @NotEmpty @Valid @Schema(description = "답안 목록") List<AnswerItem> answers,
    @Schema(description = "소요 시간(초)", example = "1200") Integer timeTaken) {

  /** 개별 답안 항목 */
  @Schema(description = "답안 항목")
  public record AnswerItem(
      @NotBlank @Schema(description = "문항 ID", example = "mq_01ARZ3NDEKTSV4RRFFQ69G5FAV")
          String questionId,
      @NotBlank @Schema(description = "선택한 답", example = "A") String selectedOption) {}
}
