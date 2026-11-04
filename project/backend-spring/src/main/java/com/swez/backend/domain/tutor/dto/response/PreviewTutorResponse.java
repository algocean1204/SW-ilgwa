/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** FastAPI 가 생성한 튜터 미리보기 응답 DTO. */
@Builder
@Schema(description = "튜터 미리보기 응답")
public record PreviewTutorResponse(
    @Schema(description = "생성된 미리보기 텍스트") String previewText,
    @Schema(description = "적용된 설정 태그 목록") List<String> settingTags,
    @Schema(description = "미리보기 TTS 음성 URL — 음성 합성 실패 시 null (텍스트만 제공)") String audioUrl) {}
