/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 북마크 응답 DTO */
@Builder
@Schema(description = "북마크 응답")
public record BookmarkResponse(
    @Schema(description = "북마크 ID") String id,
    @Schema(description = "슬라이드 인덱스") int slideIndex,
    @Schema(description = "메모") String memo,
    @Schema(description = "생성 일시") LocalDateTime createdAt) {}
