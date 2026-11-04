/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.swez.backend.domain.admin.enums.AlertEventType;
import com.swez.backend.domain.admin.enums.AlertSeverity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자 에러 알림 규칙 (admin_alert_rule 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
    name = "admin_alert_rule",
    uniqueConstraints = @UniqueConstraint(columnNames = {"credential_id", "event_type"}))
public class AdminAlertRule {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "credential_id", nullable = false)
  private AdminTelegramCredential credential;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 50)
  private AlertEventType eventType;

  @Column(nullable = false, length = 100)
  private String displayName;

  @Column(nullable = false)
  @Builder.Default
  private Boolean enabled = true;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private AlertSeverity severity = AlertSeverity.WARN;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  /** 활성/비활성 상태를 변경한다 */
  public void updateEnabled(Boolean enabled) {
    if (enabled != null) {
      this.enabled = enabled;
      this.updatedAt = LocalDateTime.now();
    }
  }

  /** 심각도를 변경한다 */
  public void updateSeverity(AlertSeverity severity) {
    if (severity != null) {
      this.severity = severity;
      this.updatedAt = LocalDateTime.now();
    }
  }
}
