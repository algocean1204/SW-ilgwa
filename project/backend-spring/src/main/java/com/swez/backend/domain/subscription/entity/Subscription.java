/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.subscription.converter.SubscriptionStatusTypeConverter;
import com.swez.backend.domain.subscription.enums.SubscriptionStatusType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 구독 계약(subscription 테이블). */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "subscription")
public class Subscription {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** 구독 플랜. subscription_plan.code 를 참조한다. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "plan", referencedColumnName = "code", nullable = false)
  private SubscriptionPlan plan;

  @Convert(converter = SubscriptionStatusTypeConverter.class)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private SubscriptionStatusType status = SubscriptionStatusType.ACTIVE;

  @Column(nullable = false)
  private LocalDateTime startedAt;

  @Column(nullable = false)
  private LocalDateTime endsAt;

  @Column(nullable = false)
  @Builder.Default
  private Boolean autoRenew = true;

  private LocalDateTime cancelledAt;

  @Column(nullable = false, updatable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  /** 구독을 취소한다. 취소 시각을 기록하고 자동갱신을 끈다. */
  public void cancel() {
    this.status = SubscriptionStatusType.CANCELLED;
    this.cancelledAt = LocalDateTime.now();
    this.autoRenew = false;
  }

  /** 구독 만료 처리 */
  public void expire() {
    this.status = SubscriptionStatusType.EXPIRED;
  }
}
