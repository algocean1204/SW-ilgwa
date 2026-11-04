/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 퀴즈 채점 결과 응답 DTO (POST /tutoring/{courseId}/lessons/{chapterId}/quiz-answers) */
@Builder
@Schema(description = "퀴즈 채점 결과")
public record QuizResultResponse(
    @Schema(description = "전체 문제 수") int totalQuestions,
    @Schema(description = "정답 수") int correctCount,
    @Schema(description = "점수 (0~100)") int score,
    @Schema(description = "개별 결과 목록") List<SingleResult> results) {

  /** 개별 퀴즈 채점 결과 */
  @Builder
  @Schema(description = "개별 퀴즈 채점 결과")
  public record SingleResult(
      @Schema(description = "퀴즈 ID") String quizId,
      @Schema(description = "선택한 보기 번호") int selectedOption,
      @Schema(description = "정답 보기 번호") int correctOption,
      @Schema(description = "정답 여부") boolean isCorrect,
      @Schema(description = "해설") String explanation) {}
}
