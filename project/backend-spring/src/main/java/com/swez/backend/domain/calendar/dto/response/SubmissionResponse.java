/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 제출물 생성 응답 DTO */
@Builder
@Schema(description = "제출물 응답")
public record SubmissionResponse(
    @Schema(description = "제출물 ID") String id,
    @Schema(description = "과제 ID") String assignmentId,
    @Schema(description = "상태") String status,
    @Schema(description = "생성 일시") LocalDateTime createdAt) {}
