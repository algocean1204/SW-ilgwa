/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.report.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 챕터별 퀴즈 정답률을 담는 개념 이해도 응답 DTO */
@Schema(description = "개념 이해도 응답")
@Builder
public record ConceptUnderstandingResponse(
    @Schema(description = "전체 평균 정답률 (0~100)", example = "75") int overallAccuracy,
    @Schema(description = "챕터별 이해도 목록") List<ChapterAccuracy> chapters) {

  /** 챕터별 퀴즈 정답률 */
  @Schema(description = "챕터별 퀴즈 정답률")
  @Builder
  public record ChapterAccuracy(
      @Schema(description = "챕터 ID") String chapterId,
      @Schema(description = "챕터 제목") String chapterTitle,
      @Schema(description = "총 퀴즈 수", example = "5") int totalQuizCount,
      @Schema(description = "정답 수", example = "4") int correctCount,
      @Schema(description = "정답률 (0~100)", example = "80") int accuracyPercent) {}
}
