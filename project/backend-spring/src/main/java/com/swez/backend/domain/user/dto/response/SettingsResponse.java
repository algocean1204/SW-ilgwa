/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 접근성·알림 설정 응답 DTO.
 *
 * <p>users.accessibility_settings, users.notification_settings JSONB 기반. user_settings 테이블
 * (push/email/telegram/language/theme 채널 설정)과는 별개.
 */
@Builder
@Schema(description = "접근성·알림 설정 응답")
public record SettingsResponse(
    @Schema(description = "일일 학습 목표 시간(분)", example = "30") int dailyStudyGoalMinutes,
    @Schema(description = "고대비 모드") boolean highContrast,
    @Schema(description = "키보드 네비게이션") boolean keyboardNavigation,
    @Schema(description = "알림 설정") NotificationsResponse notifications) {

  /** 알림 설정 중첩 응답 */
  @Builder
  @Schema(description = "알림 설정")
  public record NotificationsResponse(
      @Schema(description = "강의 알림") boolean lesson,
      @Schema(description = "과제 알림") boolean assignment,
      @Schema(description = "커뮤니티 알림") boolean community,
      @Schema(description = "마케팅 알림") boolean marketing) {}
}
