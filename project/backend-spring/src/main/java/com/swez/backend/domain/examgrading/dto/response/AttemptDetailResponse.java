/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.swez.backend.domain.examgrading.enums.AttemptStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 응시 상세 조회 공개 응답 DTO.
 *
 * <p>프론트엔드 응시 화면에 필요한 정보를 담는다. answerKeySeal, correctAnswer, explanation, sourceReference,
 * gradingMetadata, 보기의 is_correct는 절대 포함하지 않는다.
 */
@Builder
@Schema(description = "응시 상세 공개 응답 DTO — 민감 필드 전면 제외")
public record AttemptDetailResponse(
    @Schema(description = "응시 식별자", example = "attempt_20260526_0001") String attemptId,
    @Schema(description = "시험 ID", example = "exam_abc123") String examId,
    @Schema(description = "응시 상태", example = "IN_PROGRESS") AttemptStatus status,
    @Schema(description = "합격 기준 점수 비율 (0~100)", example = "60.00") BigDecimal passPercentage,
    @Schema(description = "응시 생성 시각") OffsetDateTime createdAt,
    @Schema(description = "응시용 문항 목록 — 정답·해설 제외") List<AttemptQuestionResponse> questions) {}
