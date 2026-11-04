/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.dto.request;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** 노트 수정 요청 DTO. title/content 중 하나 이상을 보낸다. */
@Schema(description = "노트 수정 요청")
public record UpdateNoteRequest(
    @Size(max = 200) @Schema(description = "노트 제목 (최대 200자)", example = "집합론 핵심 정리") String title,
    @Size(max = 5000)
        @Schema(description = "노트 내용 (최대 5000자)", example = "집합의 정의: 명확한 기준으로 구별할 수 있는 객체의 모임")
        String content) {}
