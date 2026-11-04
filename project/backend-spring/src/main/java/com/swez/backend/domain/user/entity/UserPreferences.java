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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.swez.backend.domain.auth.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학습 선호도 (user_preferences 테이블). ERD 대응 엔티티로, 현재 서비스에서는 쓰지 않는다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "user_preferences")
public class UserPreferences {

  @Id
  @Column(length = 30)
  private String id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(length = 50)
  private String gradeLevel;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String interests;

  @Column(length = 50)
  private String preferredTone;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String studyGoals;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();
}
