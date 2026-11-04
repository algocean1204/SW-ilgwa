/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * FastAPI → Spring 생성 실패 콜백 DTO.
 *
 * <p>POST /internal/exam-attempts/{attemptId}/generation-failed 에서 수신한다. X-Internal-Token 인증
 * (InternalTokenFilter).
 *
 * <p>이 DTO는 과제채점 콜백({@code AiResultCallbackRequest})과 완전히 별개이다.
 */
@Schema(description = "문항 생성 실패 콜백 (FastAPI → Spring 내부 전용)")
public record GenerationFailedCallbackRequest(
    @Schema(description = "실패 사유 (선택)", example = "LLM timeout after 300s") String reason) {}
