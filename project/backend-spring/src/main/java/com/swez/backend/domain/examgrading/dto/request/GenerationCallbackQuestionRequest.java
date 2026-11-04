/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.dto.request;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * FastAPI 생성 완료 콜백 내 개별 문항 DTO.
 *
 * <p>correct_answer·explanation·grading_metadata는 서버 전용 필드로 DB에만 저장된다. 절대 프론트 응답 DTO에 매핑하지 않는다.
 * (GradingLeakGuard 2차 감시 대상)
 */
@Schema(description = "생성된 문항 페이로드 (내부 콜백 전용)")
public record GenerationCallbackQuestionRequest(
    @NotBlank @Schema(description = "FastAPI 발급 문항 ID", example = "q_01J...") String questionId,
    @NotBlank @Schema(description = "문항 유형 템플릿 ID", example = "ko_multiple_choice")
        String templateId,
    @NotNull @Schema(description = "프론트 표시 순서 (1-based — FastAPI 콜백이 1부터 부여)", example = "1")
        Integer displayOrder,
    @NotBlank @Schema(description = "문항 본문") String stem,
    @Valid @Schema(description = "보기 목록 (객관식 전용, 그 외 null)")
        List<GenerationCallbackOptionRequest> options,
    @NotNull @Schema(description = "배점", example = "10.00") BigDecimal points,
    @Schema(description = "서버 전용 정답 — 프론트 응답 불가") String correctAnswer,
    @Schema(description = "서버 전용 해설 — 프론트 응답 불가") String explanation,
    @Schema(description = "출처 참조 — 프론트 응답 불가") String sourceReference,
    @Schema(description = "서버 전용 채점 메타데이터 JSON — 연결형·순서형·빈칸형 등 추가 데이터, 프론트 응답 불가")
        Object gradingMetadata) {}
