/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 퀴즈 개별 응답 DTO. 정답(correctOption)은 넣지 않는다. */
@Builder
@Schema(description = "퀴즈 정보 (정답 미포함)")
public record QuizResponse(
    @Schema(description = "퀴즈 ID") String quizId,
    @Schema(description = "퀴즈 순서") int quizIdx,
    @Schema(description = "연결된 슬라이드 ID") String slideId,
    @Schema(description = "질문 텍스트") String questionText,
    @Schema(description = "보기 목록") List<String> options) {}
