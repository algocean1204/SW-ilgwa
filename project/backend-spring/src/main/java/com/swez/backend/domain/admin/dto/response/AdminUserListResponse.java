/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.response;

import java.time.LocalDateTime;

import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.enums.UserRole;
import com.swez.backend.domain.auth.enums.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관리자 사용자 목록 항목. 정지 사유와 시각을 포함한다. */
@Schema(description = "관리자 사용자 목록 항목")
public record AdminUserListResponse(
    String id,
    String email,
    String nickname,
    String profileImageUrl,
    SubscriptionStatus subscriptionStatus,
    UserRole role,
    UserStatus status,
    boolean telegramLinked,
    Long telegramChatId,
    LocalDateTime createdAt,
    @Schema(description = "ACTIVE 과외 평균 진행률 (0~100), 과외 없으면 null") Double progress,
    @Schema(description = "마지막 접속 시각") LocalDateTime lastSeenAt,
    @Schema(description = "가장 최근 수정된 ACTIVE 과외 이름, 없으면 null") String activeCourseName,
    @Schema(description = "정지 사유") String suspendReason,
    @Schema(description = "정지 시각") LocalDateTime suspendedAt) {}
