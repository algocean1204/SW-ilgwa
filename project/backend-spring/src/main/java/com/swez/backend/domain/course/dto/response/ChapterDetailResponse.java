/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.swez.backend.domain.course.enums.ChapterStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 강의 상세 응답 DTO (GET /tutoring/{id}/lessons/{lessonId}) */
@Builder
@Schema(description = "강의 상세")
public record ChapterDetailResponse(
    @Schema(description = "강의 ID") String id,
    @Schema(description = "강의 순서") int order,
    @Schema(description = "강의 제목") String title,
    @Schema(description = "강의 설명") String description,
    @Schema(description = "예상 소요 시간 (분)") Integer estimatedMinutes,
    @Schema(description = "진행 상태") ChapterStatus status,
    @Schema(description = "슬라이드 수") int slideCount,
    @Schema(description = "핵심 주제 배열") List<String> keyTopics,
    @Schema(description = "북마크 목록") List<BookmarkInChapterDto> bookmarks,
    @Schema(description = "완료 일시") LocalDateTime completedAt,
    @Schema(description = "획득 XP") int xpEarned) {}
