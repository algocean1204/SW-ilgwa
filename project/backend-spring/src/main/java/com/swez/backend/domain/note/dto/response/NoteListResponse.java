/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** MemoryNotes 뷰에서 쓰는 전체 노트 목록 응답 DTO */
@Builder
@Schema(description = "전체 노트 목록 응답")
public record NoteListResponse(
    @Schema(description = "노트 ID") String id,
    @Schema(description = "제목") String title,
    @Schema(description = "내용") String content,
    @Schema(description = "노트 타입 (user_memo / ai_auto)") String type,
    @Schema(description = "별표 여부") boolean starred,
    @Schema(description = "과외 ID") String courseId,
    @Schema(description = "과외명") String courseName,
    @Schema(description = "과목") String subject,
    @Schema(description = "챕터 ID") String chapterId,
    @Schema(description = "챕터명") String chapterTitle,
    @Schema(description = "생성 일시") LocalDateTime createdAt,
    @Schema(description = "수정 일시") LocalDateTime updatedAt) {}
