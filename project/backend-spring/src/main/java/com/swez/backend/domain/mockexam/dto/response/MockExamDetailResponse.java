/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.swez.backend.domain.mockexam.enums.MockExamStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 모의고사 상세 조회 응답 DTO (GET /tutoring/{courseId}/mock-exams/{examId}) */
@Builder
@Schema(description = "모의고사 상세 (문항 포함)")
public record MockExamDetailResponse(
    @Schema(description = "모의고사 ID") String id,
    @Schema(description = "제목") String title,
    @Schema(description = "시험 유형") String examType,
    @Schema(description = "문항 수") int questionCount,
    @Schema(description = "난이도") String difficulty,
    @Schema(description = "제한 시간(분)") Integer timeLimit,
    @Schema(description = "상태") MockExamStatus status,
    @Schema(description = "총 배점") Integer totalPoints,
    @Schema(description = "생성 일시") LocalDateTime createdAt,
    @Schema(description = "문항 목록") List<MockExamQuestionResponse> questions) {}
