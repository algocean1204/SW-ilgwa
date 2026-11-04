/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 강의 상세 응답 내 북마크 정보 DTO */
@Builder
@Schema(description = "강의 내 북마크")
public record BookmarkInChapterDto(
    @Schema(description = "슬라이드 인덱스") int slideIndex,
    @Schema(description = "메모") String memo,
    @Schema(description = "생성 일시") LocalDateTime createdAt) {}
