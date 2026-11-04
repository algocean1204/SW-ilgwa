/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.dto.response;

import com.swez.backend.domain.examgrading.enums.AttemptStatus;
import com.swez.backend.domain.examgrading.enums.GenerationFailureReason;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 응시 상태 폴링 응답 DTO (GET /api/exam-forge/attempts/{attemptId}/status).
 *
 * <p>failureReason·요청/생성 문항 수는 옵셔널(null 가능)이다. 프론트는 status=FAILED +
 * failureReason=NEEDS_MORE_SOURCE 조합으로 "자료 부족" 안내·행동 제안을 띄우고, 부분 출고(READY)에서는 요청/생성
 * 문항 수로 "요청 N / 생성 M"을 표기할 수 있다. 기존 필드(attemptId·status) 동작은 그대로 유지된다.
 */
@Schema(description = "응시 상태 조회 응답")
public record AttemptStatusResponse(
    @Schema(description = "응시 식별자", example = "eat_01J...") String attemptId,
    @Schema(
            description = "응시 상태 (GENERATING/READY/FAILED/IN_PROGRESS/SUBMITTED/GRADED)",
            example = "READY")
        AttemptStatus status,
    @Schema(
            description = "FAILED 사유 코드 (옵셔널) — NEEDS_MORE_SOURCE(자료 부족)/GENERATION_ERROR. FAILED가 아니면 null",
            example = "NEEDS_MORE_SOURCE")
        GenerationFailureReason failureReason,
    @Schema(description = "요청 문항 수 (옵셔널) — 프론트 '요청 N / 생성 M' 표기용", example = "20")
        Integer requestedQuestionCount,
    @Schema(description = "실제 생성 문항 수 (옵셔널) — 부분 출고 시 요청보다 적음", example = "12")
        Integer actualQuestionCount) {

  /** 기존 호출부 호환용 생성자. 옵셔널 필드를 null로 채운다. */
  public AttemptStatusResponse(String attemptId, AttemptStatus status) {
    this(attemptId, status, null, null, null);
  }
}
