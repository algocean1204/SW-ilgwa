/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** 노트 생성 요청 DTO. type은 서비스에서 user_memo로 고정한다. */
@Schema(description = "노트 생성 요청")
public record CreateNoteRequest(
    @NotBlank
        @Size(max = 1000)
        @Schema(description = "노트 내용 (최대 1000자)", example = "집합의 정의: 명확한 기준으로 구별할 수 있는 객체의 모임")
        String content,
    @Schema(description = "관련 슬라이드 인덱스") Integer slideIndex,
    @Schema(description = "강의 내 타임스탬프 (초 단위)") Double timestamp) {}
