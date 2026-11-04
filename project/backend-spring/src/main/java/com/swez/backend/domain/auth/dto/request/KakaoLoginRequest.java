/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Kakao OAuth 로그인 요청 DTO. 프론트는 Authorization Code 만 보내고 Redirect URI 는 서버 환경변수로 고정한다. */
@Schema(description = "Kakao 소셜 로그인 요청")
public record KakaoLoginRequest(
    @NotBlank @Schema(description = "Kakao Authorization Code", example = "abc123xyz")
        String authorizationCode) {}
