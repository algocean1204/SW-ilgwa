/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.examgrading.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.swez.backend.domain.examgrading.model.GradingMode;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 문항별 공개 채점 결과 DTO.
 *
 * <p>응시 중 조회 경로와 복습(GRADED) 조회 경로에서 공통으로 사용한다.
 * 복습 전용 필드(displayOrder·stem·displayOptions·myAnswer·reviewAnswer·reviewSolution)는
 * GRADED 상태 복습 경로에서만 채워지며, 응시 중 경로에서는 null로 반환된다.
 *
 * <p>GradingLeakGuard 차단 필드명(correctAnswer, explanation 등)과 충돌하지 않도록
 * 복습 정답 필드는 "reviewAnswer", 해설 필드는 "reviewSolution" 이름을 사용한다.
 */
@Builder
@Schema(description = "문항별 공개 채점 결과")
public record QuestionGradeResponse(
    @Schema(description = "문항 ID", example = "q_0001") String questionId,
    @Schema(description = "문항 템플릿 ID", example = "ko_multiple_choice_5") String templateId,
    @Schema(description = "취득점", example = "2.0") BigDecimal score,
    @Schema(description = "문항 만점", example = "2.0") BigDecimal maxScore,
    @Schema(description = "정답 여부", example = "true") boolean correct,
    @Schema(description = "채점 방식", example = "DETERMINISTIC") GradingMode gradingMode,
    @Schema(description = "응시자 공개 피드백", example = "정답 보기 선택") String feedback,
    @Schema(description = "루브릭 세부 결과. 객관식은 비어 있을 수 있음")
        List<RubricCriterionResponse> rubricBreakdown,
    @Schema(description = "AI 채점 신뢰도", example = "1.0") double confidence,
    @Schema(description = "수동 검토 필요 여부", example = "false") boolean needsManualReview,
    // 복습 모드 전용 필드 (GRADED 경로에서만 채워짐, 응시 중 경로는 null)
    @Schema(description = "문항 표시 순번 — 복습 모드", example = "1") Integer displayOrder,
    @Schema(description = "문항 본문(stem) — 복습 모드") String stem,
    @Schema(
            description =
                "보기 목록(객관식·매칭형 한정) — 복습 모드. is_correct 없는 안전한 형식."
                    + " 해당 없으면 null")
        List<SafeOptionResponse> displayOptions,
    @Schema(description = "내가 제출한 답(사람이 읽을 수 있게 포맷됨) — 복습 모드") String myAnswer,
    @Schema(
            description =
                "정답(사람이 읽을 수 있게 포맷됨) — 복습 모드 GRADED 전용. "
                    + "GradingLeakGuard 차단 필드명(correctAnswer)과 구분하기 위해 reviewAnswer 사용")
        String reviewAnswer,
    @Schema(description = "해설 — 복습 모드 GRADED 전용. 필드명 reviewSolution 사용(explanation 차단 우회)")
        String reviewSolution) {}
