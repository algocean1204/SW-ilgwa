/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.entity;

import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.global.util.IdGenerator;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자별 앱 채널·UX 설정 (user_settings 테이블: push/email/telegram/language/theme) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "user_settings")
public class UserSettings {

  @Id
  @Column(length = 30)
  private String id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(nullable = false)
  @Builder.Default
  private Boolean pushEnabled = true;

  @Column(nullable = false)
  @Builder.Default
  private Boolean emailEnabled = true;

  /** 텔레그램 알림. 연동 전에는 알림을 보낼 수 없어 기본값은 꺼짐이다. */
  @Column(nullable = false)
  @Builder.Default
  private Boolean telegramEnabled = false;

  /** 텔레그램 자동 전송: 암기노트 전송 옵션 (화면 토글과 1:1) */
  @Column(nullable = false)
  @Builder.Default
  private Boolean telegramNoteEnabled = false;

  /** 텔레그램 자동 전송: 퀴즈 알림 옵션 (화면 토글과 1:1) */
  @Column(nullable = false)
  @Builder.Default
  private Boolean telegramQuizEnabled = false;

  /** 텔레그램 자동 전송: 친구 공유 수신 옵션 (화면 토글과 1:1) */
  @Column(nullable = false)
  @Builder.Default
  private Boolean telegramFriendEnabled = false;

  private LocalTime studyReminderTime;

  @Column(nullable = false, length = 10)
  @Builder.Default
  private String language = "ko";

  @Column(nullable = false, length = 20)
  @Builder.Default
  private String theme = "light";

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  /** 신규 사용자의 DEFAULT 설정을 만든다 */
  public static UserSettings defaultFor(User user) {
    return UserSettings.builder().id(IdGenerator.userSettings()).user(user).build();
  }

  /**
   * 텔레그램 자동 전송 옵션 부분 수정. null 인자는 기존 값을 유지한다.
   *
   * @param noteEnabled 암기노트 전송 (null이면 변경 없음)
   * @param quizEnabled 퀴즈 알림 (null이면 변경 없음)
   * @param friendEnabled 친구 공유 수신 (null이면 변경 없음)
   */
  public void updateTelegramPreferences(
      Boolean noteEnabled, Boolean quizEnabled, Boolean friendEnabled) {
    if (noteEnabled != null) this.telegramNoteEnabled = noteEnabled;
    if (quizEnabled != null) this.telegramQuizEnabled = quizEnabled;
    if (friendEnabled != null) this.telegramFriendEnabled = friendEnabled;
    this.updatedAt = LocalDateTime.now();
  }
}
