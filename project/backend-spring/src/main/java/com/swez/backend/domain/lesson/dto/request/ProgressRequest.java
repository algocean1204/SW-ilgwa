/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** 강의 진행률 기록 요청 DTO (POST /tutoring/{courseId}/lessons/{chapterId}/progress) */
@Schema(description = "강의 진행률 기록 요청")
public record ProgressRequest(
    @Schema(description = "완료한 슬라이드 수이자 다음 재개 슬라이드 인덱스", example = "3") @NotNull @Min(0)
        Integer currentSlideIdx,
    @Schema(description = "재생 시간(초)", example = "120.5") @NotNull @Min(0) BigDecimal playbackSec,
    @Schema(description = "완료 여부", example = "false") @NotNull Boolean isCompleted) {}
