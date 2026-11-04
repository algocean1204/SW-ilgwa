/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 음성 클론 샘플 업로드 응답 DTO */
@Builder
@Schema(description = "음성 클론 샘플 업로드 응답")
public record VoiceSampleResponse(
    @Schema(description = "튜터 ID") String tutorId,
    @Schema(description = "업로드된 음성 샘플 URL") String voiceSampleUrl,
    @Schema(description = "업로드 시각") LocalDateTime uploadedAt,
    @Schema(description = "처리 상태", example = "processing") String status) {}
