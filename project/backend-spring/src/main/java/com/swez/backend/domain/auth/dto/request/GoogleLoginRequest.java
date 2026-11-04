/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Google OAuth 로그인 요청 DTO. 프론트가 Google ID Token 을 전달한다. */
@Schema(description = "Google 소셜 로그인 요청")
public record GoogleLoginRequest(
    @NotBlank @Schema(description = "Google ID Token", example = "eyJhbGci...") String idToken) {}
