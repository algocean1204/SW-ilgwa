/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.swez.backend.domain.auth.converter.OauthProviderConverter;
import com.swez.backend.domain.auth.enums.OauthProvider;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 소셜 로그인 연동 계정(oauth_account 테이블). */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
    name = "oauth_account",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"}))
public class OauthAccount {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Convert(converter = OauthProviderConverter.class)
  @Column(nullable = false, length = 20)
  private OauthProvider provider;

  @Column(nullable = false, length = 255)
  private String providerUserId;

  private String email;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime linkedAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private Boolean isActive = true;
}
