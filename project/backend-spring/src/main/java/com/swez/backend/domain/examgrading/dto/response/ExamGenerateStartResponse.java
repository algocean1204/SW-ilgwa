/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.dto.response;

import com.swez.backend.domain.examgrading.enums.AttemptStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 모의고사 생성 시작 응답 DTO. 응시 ID와 GENERATING 상태를 즉시 반환한다.
 *
 * <p>클라이언트는 이 attempt_id로 GET 상태를 폴링한다. READY 상태가 되면 시험을 시작할 수 있다.
 */
@Schema(description = "모의고사 생성 시작 응답 — attempt_id와 GENERATING 상태 즉시 반환")
public record ExamGenerateStartResponse(
    @Schema(description = "응시 식별자 — 상태 폴링에 사용", example = "eat_01J...") String attemptId,
    @Schema(description = "응시 상태 (GENERATING 반환)", example = "GENERATING") AttemptStatus status) {}
