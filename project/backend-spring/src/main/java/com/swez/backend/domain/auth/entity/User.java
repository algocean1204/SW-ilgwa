/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.swez.backend.domain.auth.converter.SubscriptionStatusConverter;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.enums.UserRole;
import com.swez.backend.domain.auth.enums.UserStatus;
import com.swez.backend.global.entity.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 서비스 사용자 엔티티(users 테이블). */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "users")
public class User extends BaseTimeEntity {

  @Id
  @Column(length = 30)
  private String id;

  @Column(nullable = false, unique = true)
  private String email;

  /** OAuth 전용 계정은 null */
  private String passwordHash;

  @Column(nullable = false, length = 100)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private UserRole role = UserRole.USER;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private UserStatus status = UserStatus.ACTIVE;

  @Column(columnDefinition = "TEXT")
  private String profileImageUrl;

  private Long telegramChatId;

  private String telegramLinkToken;

  /** 활성 튜터. tutor.tutor_id 를 참조한다. */
  @Column(name = "current_tutor_id", length = 30)
  private String currentTutorId;

  @Column(length = 50)
  private String gradeLevel;

  @Column(length = 100)
  private String major;

  @Column(length = 100)
  private String education;

  @Column(columnDefinition = "TEXT")
  private String learningGoal;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String interests;

  @Column(length = 50)
  private String preferredTone;

  /** 일일 학습 목표 시간(분). 온보딩에서 정하고 설정 페이지에서 바꿀 수 있다. */
  @Column(nullable = false)
  @Builder.Default
  private Integer dailyStudyGoalMinutes = 30;

  @Column(nullable = false)
  @Builder.Default
  private Boolean onboardingCompleted = false;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String notificationSettings;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String accessibilitySettings;

  @Convert(converter = SubscriptionStatusConverter.class)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private SubscriptionStatus subscriptionStatus = SubscriptionStatus.FREE;

  private LocalDateTime subscriptionStartedAt;

  private LocalDateTime subscriptionExpiresAt;

  /** 마지막 활동 시각. JWT 인증 때마다 갱신한다. */
  private LocalDateTime lastSeenAt;

  /** 관리자가 정지할 때 입력한 사유. */
  @Column(columnDefinition = "TEXT")
  private String suspendReason;

  /** 관리자가 정지한 시각. */
  private LocalDateTime suspendedAt;

  /** 회원 탈퇴(소프트 삭제) 시각. null 이면 탈퇴하지 않은 회원이다. */
  private LocalDateTime withdrawnAt;

  /** 온보딩 완료 처리 */
  public void completeOnboarding() {
    this.onboardingCompleted = true;
  }

  /** 프로필을 부분 수정한다. null 파라미터는 기존 값을 유지한다. */
  public void updateProfile(
      String nickname,
      String profileImageUrl,
      String gradeLevel,
      String major,
      String education,
      String learningGoal,
      String interests,
      String preferredTone) {
    if (nickname != null) this.nickname = nickname;
    if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
    if (gradeLevel != null) this.gradeLevel = gradeLevel;
    if (major != null) this.major = major;
    if (education != null) this.education = education;
    if (learningGoal != null) this.learningGoal = learningGoal;
    if (interests != null) this.interests = interests;
    if (preferredTone != null) this.preferredTone = preferredTone;
  }

  /** 온보딩 정보 일괄 저장 */
  public void updateOnboardingInfo(
      String gradeLevel,
      String learningGoal,
      String interests,
      String preferredTone,
      Integer dailyStudyGoalMinutes) {
    this.gradeLevel = gradeLevel;
    this.learningGoal = learningGoal;
    this.interests = interests;
    this.preferredTone = preferredTone;
    if (dailyStudyGoalMinutes != null) this.dailyStudyGoalMinutes = dailyStudyGoalMinutes;
  }

  /** 프로필 이미지 URL 갱신 */
  public void updateProfileImageUrl(String url) {
    this.profileImageUrl = url;
  }

  /** 프로필 이미지 URL 제거 */
  public void clearProfileImageUrl() {
    this.profileImageUrl = null;
  }

  /** 일일 학습 목표 시간 수정 */
  public void updateDailyStudyGoalMinutes(int minutes) {
    this.dailyStudyGoalMinutes = minutes;
  }

  /** 텔레그램 연동 토큰 저장 */
  public void issueTelegramLinkToken(String token) {
    this.telegramLinkToken = token;
  }

  /** 텔레그램 연동 완료 후 일회성 토큰을 제거한다 */
  public void clearTelegramLinkToken() {
    this.telegramLinkToken = null;
  }

  /** 텔레그램 chat_id mirror 컬럼 동기화 */
  public void updateTelegramChatId(Long telegramChatId) {
    this.telegramChatId = telegramChatId;
  }

  /** 관리자가 사용자를 정지한다. 사유와 시각을 함께 기록한다. */
  public void suspend(String reason) {
    this.status = UserStatus.SUSPENDED;
    this.suspendReason = reason;
    this.suspendedAt = LocalDateTime.now();
  }

  /** 관리자가 정지를 해제한다. 사유와 시각을 초기화한다. */
  public void unsuspend() {
    this.status = UserStatus.ACTIVE;
    this.suspendReason = null;
    this.suspendedAt = null;
  }

  /** 회원 탈퇴(소프트 삭제). 상태를 WITHDRAWN 으로 바꾸고 탈퇴 시각을 기록하며 데이터는 보존한다. */
  public void withdraw() {
    this.status = UserStatus.WITHDRAWN;
    this.withdrawnAt = LocalDateTime.now();
  }

  /** 탈퇴한 회원인지 여부 */
  public boolean isWithdrawn() {
    return this.status == UserStatus.WITHDRAWN;
  }

  /** 정지된 회원인지 여부 */
  public boolean isSuspended() {
    return this.status == UserStatus.SUSPENDED;
  }

  /** 알림 설정 JSONB 업데이트 */
  public void updateNotificationSettings(String notificationSettingsJson) {
    this.notificationSettings = notificationSettingsJson;
  }

  /** 접근성 설정 JSONB 업데이트 */
  public void updateAccessibilitySettings(String accessibilitySettingsJson) {
    this.accessibilitySettings = accessibilitySettingsJson;
  }

  /** 활성 AI 튜터 지정 */
  public void selectTutor(String tutorId) {
    this.currentTutorId = tutorId;
  }

  /** 구독 상태를 맞춘다. 결제 확인·취소·만료 때 호출한다. */
  public void updateSubscription(
      SubscriptionStatus status, LocalDateTime startedAt, LocalDateTime expiresAt) {
    this.subscriptionStatus = status;
    this.subscriptionStartedAt = startedAt;
    this.subscriptionExpiresAt = expiresAt;
  }

  /** 마지막 활동 시각을 갱신한다. 인증 필터에서 호출한다. */
  public void updateLastSeenAt() {
    this.lastSeenAt = LocalDateTime.now();
  }
}
