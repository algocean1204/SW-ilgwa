/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.dto.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * FastAPI 생성 콜백 내 개별 보기 항목 DTO.
 *
 * <p>is_correct 필드는 서버 전용으로 DB에 저장된다. 절대 프론트 응답 DTO에 매핑하지 않는다.
 */
@Schema(description = "문항 보기 항목 (내부 콜백 전용)")
public record GenerationCallbackOptionRequest(
    @NotBlank @Schema(description = "보기 레이블 (A/B/C/D 등)", example = "A") String label,
    @NotBlank @Schema(description = "보기 텍스트") String text,
    @Schema(description = "정답 여부 — 서버 전용, 프론트 응답 불가") boolean isCorrect) {}
