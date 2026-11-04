/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.dto.request;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** 북마크 생성·갱신 요청 DTO */
@Schema(description = "북마크 생성 요청 (동일 슬라이드 재호출 시 memo 업데이트)")
public record CreateBookmarkRequest(
    @NotNull @Schema(description = "슬라이드 인덱스", example = "3") Integer slideIndex,
    @Schema(description = "메모") String memo) {}
