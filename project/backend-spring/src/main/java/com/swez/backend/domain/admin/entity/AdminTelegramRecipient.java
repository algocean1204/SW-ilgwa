/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자 텔레그램 알림 수신자 (admin_telegram_recipient 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
    name = "admin_telegram_recipient",
    uniqueConstraints = @UniqueConstraint(columnNames = {"credential_id", "chat_id"}))
public class AdminTelegramRecipient {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "credential_id", nullable = false)
  private AdminTelegramCredential credential;

  @Column(nullable = false, length = 100)
  private String nickname;

  @Column(name = "chat_id", nullable = false)
  private Long chatId;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
