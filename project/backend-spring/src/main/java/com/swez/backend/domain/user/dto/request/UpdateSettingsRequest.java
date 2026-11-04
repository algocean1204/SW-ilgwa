/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 접근성·알림 설정 업데이트 요청 DTO. null 필드는 기존 값을 유지한다 (부분 병합). */
@Schema(description = "설정 업데이트 요청 (null 필드는 변경 없음)")
public record UpdateSettingsRequest(
    @Schema(description = "일일 학습 목표 시간(분)", example = "60") Integer dailyStudyGoalMinutes,
    @Schema(description = "고대비 모드", example = "false") Boolean highContrast,
    @Schema(description = "키보드 네비게이션", example = "false") Boolean keyboardNavigation,
    @Schema(description = "알림 설정") NotificationsDto notifications) {

  /** 알림 설정 중첩 DTO */
  @Schema(description = "알림 설정 (null 필드는 기존 값 유지)")
  public record NotificationsDto(
      @Schema(description = "강의 알림") Boolean lesson,
      @Schema(description = "과제 알림") Boolean assignment,
      @Schema(description = "커뮤니티 알림") Boolean community,
      @Schema(description = "마케팅 알림") Boolean marketing) {}
}
