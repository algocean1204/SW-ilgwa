/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.assignment.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** 과제 답안 제출 요청 DTO */
@Schema(description = "과제 답안 제출 요청")
public record SubmitAnswerRequest(
    @NotEmpty @Valid @Schema(description = "답안 목록") List<AnswerItem> answers) {

  /** 개별 답안 항목 */
  @Schema(description = "개별 답안")
  public record AnswerItem(
      @NotNull @Schema(description = "문제 번호", example = "1") Integer questionId,
      @NotNull @Schema(description = "답안 내용", example = "42") String answer) {}
}
