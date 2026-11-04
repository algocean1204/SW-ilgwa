/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 토큰 갱신 응답 DTO */
@Builder
@Schema(description = "토큰 갱신 응답")
public record TokenRefreshResponse(
    @Schema(description = "새로 발급된 JWT 액세스 토큰", example = "eyJhbGci...") String accessToken) {}
