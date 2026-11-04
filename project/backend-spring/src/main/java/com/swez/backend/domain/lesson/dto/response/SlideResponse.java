/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 슬라이드 개별 응답 DTO */
@Builder
@Schema(description = "슬라이드 정보")
public record SlideResponse(
    @Schema(description = "슬라이드 ID") String slideId,
    @Schema(description = "슬라이드 순서 (0부터)") int slideIdx,
    @Schema(description = "슬라이드 제목") String title,
    @Schema(description = "슬라이드 콘텐츠") String content,
    @Schema(description = "오디오 URL") String audioUrl,
    @Schema(description = "재생 시간(초)") BigDecimal durationSec) {}
