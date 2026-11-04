/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 구독 플랜 마스터 데이터(subscription_plan 테이블). 시드로 넣고 자연키를 PK 로 쓴다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "subscription_plan")
public class SubscriptionPlan {

  /** 플랜 코드 자연키 (free / pro / premium) */
  @Id
  @Column(length = 20)
  private String code;

  @Column(nullable = false)
  @Builder.Default
  private Integer priceKrw = 0;

  @Column(nullable = false)
  @Builder.Default
  private Integer periodDays = 30;

  @Column(nullable = false)
  private Integer maxCourses;

  /** NULL이면 무제한 */
  private Integer maxSlidesPerCourse;

  @Column(nullable = false)
  @Builder.Default
  private Boolean canUseTelegram = false;

  @Column(nullable = false)
  @Builder.Default
  private Boolean canCustomizeTutor = false;

  /** 플랜별 기능 목록 JSONB */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private String features = "[]";

  /**
   * 구독 활성화 시 즉시 지급하는 코인 수량.
   * 코인 구매 번들 단가로 환산한 값이다(V20): pro 5코인(≈15,000원치=COIN_5), premium 22코인(≈50,000원치=COIN_20).
   * free 플랜은 0코인.
   */
  @Column(nullable = false)
  @Builder.Default
  private Integer coinGrant = 0;

  @Column(nullable = false)
  @Builder.Default
  private Boolean isActive = true;
}
