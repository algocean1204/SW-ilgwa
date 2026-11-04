/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.swez.backend.domain.auth.enums.SubscriptionStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 사용자 프로필 응답 DTO */
@Builder
@Schema(description = "사용자 프로필 응답")
public record UserProfileResponse(
    @Schema(description = "사용자 ID (ULID)", example = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV") String id,
    @Schema(description = "이메일") String email,
    @Schema(description = "닉네임") String nickname,
    @Schema(description = "프로필 이미지 URL") String profileImageUrl,
    @Schema(description = "학년") String grade,
    @Schema(description = "전공") String major,
    @Schema(description = "학교명") String education,
    @Schema(description = "관심 과목 배열") List<String> interests,
    @Schema(description = "학습 목표") String learningGoal,
    @Schema(description = "선호 말투") String preferredTone,
    @Schema(description = "구독 상태") SubscriptionStatus subscriptionStatus,
    @Schema(description = "구독 만료일") LocalDateTime subscriptionExpiresAt,
    @Schema(description = "XP (TODO: user_game_state 연동 전 0 반환)") int xp,
    @Schema(description = "레벨 (TODO: user_game_state 연동 전 0 반환)") int level,
    @Schema(description = "포인트 (TODO: user_game_state 연동 전 0 반환)") int points,
    @Schema(description = "텔레그램 연동 여부") boolean telegramLinked,
    @Schema(description = "온보딩 완료 여부") boolean onboardingCompleted,
    @Schema(description = "계정 생성일") LocalDateTime createdAt,
    @Schema(description = "접근성·알림 설정") SettingsResponse settings) {}
