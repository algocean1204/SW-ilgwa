/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 사용자 프로필 이미지 업로드 응답 DTO */
@Builder
@Schema(description = "프로필 이미지 업로드 응답")
public record UserProfileImageResponse(
    @Schema(description = "업로드된 프로필 이미지 URL") String profileImageUrl) {}
