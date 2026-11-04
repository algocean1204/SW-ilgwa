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

import com.swez.backend.domain.auth.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 텔레그램 송수신 로그 (telegram_message 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "telegram_message")
public class TelegramMessage {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 10)
  private String direction;

  @Column(length = 30)
  private String messageType;

  @Column(columnDefinition = "TEXT")
  private String content;

  @Column(length = 500)
  private String imageUrl;

  @Column(length = 30)
  private String referenceType;

  @Column(length = 30)
  private String referenceId;

  @Column(length = 50)
  private String telegramMsgId;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
