/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 프로필 이미지 업로드 응답 DTO */
@Builder
@Schema(description = "프로필 이미지 업로드 응답")
public record ProfileImageResponse(
    @Schema(description = "튜터 ID") String tutorId,
    @Schema(description = "업로드된 프로필 이미지 URL") String profileImageUrl,
    @Schema(description = "업로드 시각") LocalDateTime uploadedAt) {}
