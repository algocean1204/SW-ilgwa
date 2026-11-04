/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.dto.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI 모의고사 생성 요청 DTO (POST /tutoring/{courseId}/mock-exams/generate) */
@Schema(description = "AI 모의고사 생성 요청")
public record GenerateExamRequest(
    @NotBlank @Schema(description = "시험 유형 (midterm, final, practice 등)", example = "midterm")
        String examType,
    @NotNull @Min(1) @Max(50) @Schema(description = "문항 수", example = "10") Integer questionCount,
    @NotBlank @Schema(description = "난이도 (easy, medium, hard)", example = "medium")
        String difficulty,
    @Schema(description = "집중 주제 목록") List<String> focusTopics,
    @Min(1) @Schema(description = "제한 시간(분)", example = "30") Integer timeLimit) {}
