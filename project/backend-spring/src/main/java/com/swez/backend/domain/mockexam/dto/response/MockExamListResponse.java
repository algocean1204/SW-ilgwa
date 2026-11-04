/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.dto.response;

import java.time.LocalDateTime;

import com.swez.backend.domain.mockexam.enums.MockExamStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 모의고사 목록 아이템 응답 DTO (GET /tutoring/{courseId}/mock-exams) */
@Builder
@Schema(description = "모의고사 목록 아이템")
public record MockExamListResponse(
    @Schema(description = "모의고사 ID") String id,
    @Schema(description = "제목") String title,
    @Schema(description = "시험 유형") String examType,
    @Schema(description = "문항 수") int questionCount,
    @Schema(description = "난이도") String difficulty,
    @Schema(description = "제한 시간(분)") Integer timeLimit,
    @Schema(description = "상태") MockExamStatus status,
    @Schema(description = "총 배점") Integer totalPoints,
    @Schema(description = "생성 일시") LocalDateTime createdAt,
    @Schema(
            description =
                "cutover=true 경로에서 생성된 응시 ID. "
                    + "프론트는 이 값이 null이 아닐 때 examgrading 경로(detail·submit·result)를 사용한다. "
                    + "null이면 레거시 mockexam 경로를 사용한다.",
            example = "eat_01ARZ3NDEKTSV4RRFFQ69G5FAV",
            nullable = true)
        String attemptId,
    @Schema(
            description =
                "생성 실패 사유 코드(NEEDS_MORE_SOURCE 등). 최신 attempt가 FAILED일 때만 non-null. "
                    + "프론트는 이 값이 있으면 '출제 중'이 아니라 '생성 실패'로 표시하고 폴링을 멈춘다. "
                    + "프론트 GenerationOutcomeMeta.generationFailureReason 과 키를 맞춘다.",
            example = "NEEDS_MORE_SOURCE",
            nullable = true)
        String generationFailureReason) {}
