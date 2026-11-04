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
import com.swez.backend.domain.subscription.converter.PaymentStatusConverter;
import com.swez.backend.domain.subscription.enums.PaymentStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 결제 트랜잭션 기록(payment 테이블). */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "payment")
public class Payment {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** 연결된 구독. 환불 등으로 구독이 삭제되면 NULL 일 수 있다. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subscription_id")
  private Subscription subscription;

  @Column(nullable = false)
  private Integer amount;

  /** 토스페이먼츠 결제 키 */
  @Column(unique = true)
  private String tossPaymentKey;

  /** 토스페이먼츠 주문 ID */
  private String tossOrderId;

  @Convert(converter = PaymentStatusConverter.class)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private PaymentStatus status = PaymentStatus.PAID;

  private LocalDateTime paidAt;

  /** 소프트 삭제 시각. NULL 이면 삭제되지 않은 결제다. */
  private LocalDateTime deletedAt;

  @Column(nullable = false, updatable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  /** 환불 처리 */
  public void refund() {
    this.status = PaymentStatus.REFUNDED;
    this.updatedAt = LocalDateTime.now();
  }

  /** 소프트 삭제 */
  public void softDelete() {
    this.deletedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }
}
