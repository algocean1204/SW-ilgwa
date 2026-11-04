/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

/** 슈퍼 관리자 텔레그램 봇 인증 정보 (admin_telegram_credential 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "admin_telegram_credential")
public class AdminTelegramCredential {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String botTokenEncrypted;

  @Column(length = 32)
  private String botTokenFingerprint;

  private Long adminChatId;

  private LocalDateTime lastRotatedAt;

  private LocalDateTime lastHealthCheckAt;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  /** 봇 토큰 암호문과 fingerprint를 교체한다. */
  public void rotateBotToken(String encryptedToken, String fingerprint) {
    this.botTokenEncrypted = encryptedToken;
    this.botTokenFingerprint = fingerprint;
    this.lastRotatedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  /** 관리자 알림 수신 chat_id를 변경한다. */
  public void updateAdminChatId(Long adminChatId) {
    this.adminChatId = adminChatId;
    this.updatedAt = LocalDateTime.now();
  }

  /** Telegram getMe 헬스체크 성공 시각을 기록한다. */
  public void markHealthCheckSuccess() {
    this.lastHealthCheckAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }
}
