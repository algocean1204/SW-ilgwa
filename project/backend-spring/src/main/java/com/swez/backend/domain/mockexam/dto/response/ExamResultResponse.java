/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 모의고사 결과 상세 응답 DTO (GET /tutoring/{courseId}/mock-exams/{examId}/results) */
@Builder
@Schema(description = "모의고사 결과 상세")
public record ExamResultResponse(
    @Schema(description = "모의고사 ID") String examId,
    @Schema(description = "제목") String title,
    @Schema(description = "점수") int score,
    @Schema(description = "총 배점") int totalPoints,
    @Schema(description = "등급") String grade,
    @Schema(description = "소요 시간(초)") Integer timeTaken,
    @Schema(description = "제출 일시") LocalDateTime submittedAt,
    @Schema(description = "분석 (JSON)") String analysis,
    @Schema(description = "문항별 결과") List<QuestionResultItem> questionResults) {

  /** 문항별 결과 아이템 */
  @Builder
  @Schema(description = "문항별 결과")
  public record QuestionResultItem(
      @Schema(description = "문항 ID") String questionId,
      @Schema(description = "문항 순서") int questionIdx,
      @Schema(description = "문제 텍스트") String questionText,
      @Schema(description = "선택지") String options,
      @Schema(description = "선택한 답") String selectedOption,
      @Schema(description = "정답") String correctOption,
      @Schema(description = "정답 여부") boolean correct,
      @Schema(description = "해설") String explanation,
      @Schema(description = "배점") int points) {}
}
