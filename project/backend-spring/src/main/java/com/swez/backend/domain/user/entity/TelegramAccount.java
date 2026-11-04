/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.swez.backend.domain.auth.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 텔레그램 계정 연동 (telegram_account 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "telegram_account")
public class TelegramAccount {

  @Id
  @Column(length = 30)
  private String id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(nullable = false, unique = true)
  private Long telegramChatId;

  private String telegramUsername;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime linkedAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private Boolean isActive = true;

  private LocalDateTime lastInteractionAt;

  /** 관리자 API에서 chat_id를 설정하거나 재연동한다. */
  public void link(Long telegramChatId) {
    this.telegramChatId = telegramChatId;
    this.isActive = true;
    this.linkedAt = LocalDateTime.now();
  }

  /** 봇 딥링크로 자동 연동한다. chat_id와 username을 함께 저장한다 */
  public void linkWithUsername(Long telegramChatId, String telegramUsername) {
    this.telegramChatId = telegramChatId;
    this.telegramUsername = telegramUsername;
    this.isActive = true;
    this.linkedAt = LocalDateTime.now();
  }

  /** 관리자 API에서 연동을 비활성화한다. chat_id 정본 값은 감사·재연동을 위해 보존한다. */
  public void unlink() {
    this.isActive = false;
  }
}
