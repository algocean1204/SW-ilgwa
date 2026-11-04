/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 퀴즈 목록 응답 DTO (GET /tutoring/{courseId}/lessons/{chapterId}/quizzes) */
@Builder
@Schema(description = "퀴즈 목록 응답")
public record QuizListResponse(
    @Schema(description = "퀴즈 목록") List<QuizResponse> quizzes,
    @Schema(description = "전체 퀴즈 수") int totalQuizzes) {}
