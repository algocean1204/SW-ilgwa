/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.swez.backend.domain.examgrading.model.GradingStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 응시 결과 조회 공개 응답 DTO.
 *
 * <p>채점이 완료된 응시의 결과를 반환한다. 문항별 채점 결과에는 정답·해설·출처·채점 메타데이터가 포함되지 않는다. GradingLeakGuard가 직렬화 후 민감 필드
 * 누수를 2차 검증한다.
 */
@Builder
@Schema(description = "응시 결과 공개 응답 DTO — 채점 요약 + 문항별 채점 결과")
public record AttemptResultResponse(
    @Schema(description = "응시 식별자", example = "attempt_20260526_0001") String attemptId,
    @Schema(description = "시험 ID", example = "exam_abc123") String examId,
    @Schema(description = "총 취득점", example = "78.50") BigDecimal totalScore,
    @Schema(description = "총 만점", example = "100.00") BigDecimal maxScore,
    @Schema(description = "백분율 점수 (0~100)", example = "78.50") BigDecimal percentage,
    @Schema(description = "합격 여부", example = "true") boolean passed,
    @Schema(description = "채점 상태", example = "GRADED") GradingStatus gradingStatus,
    @Schema(description = "채점 완료 시각") OffsetDateTime gradedAt,
    @Schema(description = "문항별 채점 결과 — 정답·해설 제외") List<QuestionGradeResponse> results,
    @Schema(description = "AI 총평 본문 — 분석 실패 시 null", example = "전반적으로 잘했어요...")
        String analysis,
    @Schema(description = "AI 진단 약점 주제 목록 — 분석 실패/만점 시 빈 목록 또는 null")
        List<String> weakTopics) {}
