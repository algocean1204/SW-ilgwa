/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI 채점 결과 콜백 요청 DTO. FastAPI → Spring 으로 POST /internal/submissions/{id}/ai-result 를 호출한다. */
@Schema(description = "AI 채점 결과 콜백 요청 (내부 전용)")
public record AiResultCallbackRequest(
    @Schema(description = "OCR 추출 텍스트") String ocrText,
    @Schema(description = "AI 채점 점수") Integer score,
    @Schema(description = "AI 피드백") String feedback,
    @NotNull @Schema(description = "AI 신뢰도 (0.0~1.0)") BigDecimal aiConfidence,
    @Schema(description = "OCR 실패 페이지 수", defaultValue = "0") Integer ocrFailedPages,
    @Schema(description = "전체 페이지 수") Integer totalPages,
    @Schema(description = "재제출 여부", defaultValue = "false") Boolean isRetry,
    @NotBlank
        @Pattern(
            regexp = "queued|grading|done|failed",
            message = "허용된 값: queued, grading, done, failed")
        @Schema(description = "전이할 상태 (queued/grading/done/failed)")
        String statusTransition) {}
