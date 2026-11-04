/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 슬라이드 목록 응답 DTO (GET /tutoring/{courseId}/lessons/{chapterId}/slides) */
@Builder
@Schema(description = "슬라이드 목록 응답")
public record SlideListResponse(
    @Schema(description = "슬라이드 목록") List<SlideResponse> slides,
    @Schema(description = "전체 슬라이드 수") int totalSlides,
    @Schema(description = "완료한 슬라이드 수") int completedSlides) {}
