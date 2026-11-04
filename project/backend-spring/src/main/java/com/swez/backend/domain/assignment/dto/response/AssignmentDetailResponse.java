/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.assignment.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 과제 상세 응답 DTO (GET /tutoring/{courseId}/assignments/{assignmentId}) */
@Builder
@Schema(description = "과제 상세")
public record AssignmentDetailResponse(
    @Schema(description = "과제 ID") String id,
    @Schema(description = "과제 제목") String title,
    @Schema(description = "과제 설명") String description,
    @Schema(description = "과제 상태 (active/completed)") String status,
    @Schema(description = "마감일") LocalDateTime dueDate,
    @Schema(description = "총 문제 수") int totalQuestions,
    @Schema(description = "최대 점수") Integer maxScore,
    @Schema(description = "문제 목록") List<QuestionItem> questions,
    @Schema(description = "이전 제출 답안 (없으면 null)") String previousAnswer,
    @Schema(description = "생성일") LocalDateTime createdAt) {

  /** 개별 문제 항목 */
  @Builder
  @Schema(description = "문제")
  public record QuestionItem(
      @Schema(description = "문제 번호") int questionId,
      @Schema(description = "문제 내용") String content) {}
}
