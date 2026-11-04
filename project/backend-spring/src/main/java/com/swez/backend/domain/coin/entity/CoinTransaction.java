/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.entity;

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
import com.swez.backend.domain.coin.converter.CoinTransactionTypeConverter;
import com.swez.backend.domain.coin.enums.CoinTransactionType;
import com.swez.backend.domain.subscription.entity.Payment;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 코인 사용/구매 이벤트 소싱 기록 (coin_transaction 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "coin_transaction")
public class CoinTransaction {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wallet_id", nullable = false)
  private CoinWallet wallet;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Convert(converter = CoinTransactionTypeConverter.class)
  @Column(name = "type", nullable = false, length = 20)
  private CoinTransactionType type;

  @Column(nullable = false)
  private Integer amount;

  @Column(name = "balance_after", nullable = false)
  private Integer balanceAfter;

  /** 참조 대상 ID. 슬라이드·모의고사 등 대상 엔티티의 PK */
  @Column(name = "ref_id", length = 30)
  private String refId;

  /** 연결된 결제. 코인 구매 때만 있고, 환불 등으로 결제가 삭제되면 NULL이 된다. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_id")
  private Payment payment;

  @Column(length = 200)
  private String note;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
