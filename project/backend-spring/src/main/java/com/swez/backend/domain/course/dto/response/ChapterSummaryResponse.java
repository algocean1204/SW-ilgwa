/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.dto.response;

import java.time.LocalDateTime;

import com.swez.backend.domain.course.enums.ChapterStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 강의 목록 아이템 응답 DTO (GET /tutoring/{id}/lessons) */
@Builder
@Schema(description = "강의 목록 아이템")
public record ChapterSummaryResponse(
    @Schema(description = "강의 ID") String id,
    @Schema(description = "강의 순서") int order,
    @Schema(description = "강의 제목") String title,
    @Schema(description = "강의 설명") String description,
    @Schema(description = "예상 소요 시간 (분)") Integer estimatedMinutes,
    @Schema(description = "진행 상태") ChapterStatus status,
    @Schema(description = "슬라이드 수") int slideCount,
    @Schema(description = "완료한 슬라이드 수", example = "3") int completedSlides,
    @Schema(description = "완료 일시") LocalDateTime completedAt) {}
