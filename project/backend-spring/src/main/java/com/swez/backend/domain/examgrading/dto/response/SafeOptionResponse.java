/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * is_correct 필드를 뺀 응시용 공개 보기 DTO.
 *
 * <p>QuestionOption 도메인 record(label/text/correct)에서 correct 필드를 제거한다. GradingLeakGuard가 직렬화 후
 * isCorrect·is_correct 필드명 존재 여부를 추가 검사한다.
 */
@Builder
@Schema(description = "응시용 안전한 보기 DTO — 정답 여부 제외")
public record SafeOptionResponse(
    @Schema(description = "보기 레이블", example = "A") String label,
    @Schema(description = "보기 텍스트", example = "요청 성공") String text) {}
