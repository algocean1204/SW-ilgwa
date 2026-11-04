/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.dto.response;

import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.enums.UserRole;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 로그인 / 회원가입 성공 응답 DTO */
@Builder
@Schema(description = "로그인 응답")
public record LoginResponse(
    @Schema(description = "JWT 액세스 토큰", example = "eyJhbGci...") String accessToken,
    @Schema(description = "사용자 ID", example = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV") String userId,
    @Schema(description = "이메일", example = "user@example.com") String email,
    @Schema(description = "닉네임", example = "홍길동") String nickname,
    @Schema(description = "온보딩 완료 여부", example = "false") Boolean onboardingCompleted,
    @Schema(description = "구독 상태") SubscriptionStatus subscriptionStatus,
    @Schema(description = "사용자 권한") UserRole role) {}
