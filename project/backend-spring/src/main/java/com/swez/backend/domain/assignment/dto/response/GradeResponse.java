/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.assignment.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 채점 결과 응답 DTO (GET /tutoring/{courseId}/assignments/{assignmentId}/grades) */
@Builder
@Schema(description = "채점 결과 상세")
public record GradeResponse(
    @Schema(description = "제출 ID") String submissionId,
    @Schema(description = "과제 ID") String assignmentId,
    @Schema(description = "채점 점수") Integer score,
    @Schema(description = "최대 점수") Integer maxScore,
    @Schema(description = "AI 피드백") String feedback,
    @Schema(description = "AI 신뢰도 (0.000~1.000)") BigDecimal aiConfidence,
    @Schema(description = "OCR 실패 페이지 수") Integer ocrFailedPages,
    @Schema(description = "전체 페이지 수") Integer totalPages,
    @Schema(description = "채점 완료 일시") LocalDateTime gradedAt,
    @Schema(description = "제출 일시") LocalDateTime submittedAt) {}
