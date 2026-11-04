/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.assignment.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 과제 제출 결과 응답 DTO (POST /tutoring/{courseId}/assignments/{assignmentId}/submit) */
@Builder
@Schema(description = "과제 제출 결과")
public record SubmissionResponse(
    @Schema(description = "제출 ID") String submissionId,
    @Schema(description = "과제 ID") String assignmentId,
    @Schema(description = "제출 상태 (queued/grading/done/failed)") String status,
    @Schema(description = "제출 일시") LocalDateTime submittedAt) {}
