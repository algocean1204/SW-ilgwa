/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 노트 응답 DTO */
@Builder
@Schema(description = "노트 응답")
public record NoteResponse(
    @Schema(description = "노트 ID") String id,
    @Schema(description = "제목") String title,
    @Schema(description = "내용") String content,
    @Schema(description = "노트 타입") String type,
    @Schema(description = "슬라이드 인덱스") Integer slideIndex,
    @Schema(description = "타임스탬프 (초)") Double timestamp,
    @Schema(description = "별표 여부") boolean starred,
    @Schema(description = "생성 일시") LocalDateTime createdAt) {}
