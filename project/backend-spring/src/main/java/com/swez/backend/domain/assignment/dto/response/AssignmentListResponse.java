/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.assignment.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 과제 목록 아이템 응답 DTO (GET /tutoring/{courseId}/assignments) */
@Builder
@Schema(description = "과제 목록 아이템")
public record AssignmentListResponse(
    @Schema(description = "과제 ID") String id,
    @Schema(description = "과제 제목") String title,
    @Schema(description = "과제 상태 (active/completed)") String status,
    @Schema(description = "마감일") LocalDateTime dueDate,
    @Schema(description = "총 문제 수") int totalQuestions,
    @Schema(description = "최대 점수") Integer maxScore,
    @Schema(description = "생성일") LocalDateTime createdAt) {}
