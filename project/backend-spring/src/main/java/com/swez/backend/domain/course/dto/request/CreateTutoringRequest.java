/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** 과외 생성 요청 DTO (POST /tutoring) */
@Schema(description = "과외 생성 요청")
public record CreateTutoringRequest(
    @Schema(description = "과외 제목") @NotBlank String title,
    @Schema(description = "주제 (sourceType이 topic일 때)") String topic,
    @Schema(description = "과목") @NotBlank String subject,
    @Schema(description = "자료 유형 (pdf 또는 topic)") @NotBlank String sourceType,
    @Schema(description = "PDF URL (sourceType이 pdf일 때)") String sourceRef,
    @Schema(description = "기간 (일)") Integer durationDays,
    @Schema(description = "난이도") String difficulty,
    @Schema(description = "튜터 ID") String tutorId,
    @Schema(description = "강의 수 (1~30)") @NotNull @Min(1) @Max(30) Integer lessonCount,
    @Schema(description = "대상 수준") String audienceLevel,
    @Schema(description = "학습 목표") String learningGoal) {}
