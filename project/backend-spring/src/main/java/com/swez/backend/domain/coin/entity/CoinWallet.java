/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.entity;

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

/** 사용자별 코인 지갑, 1인 1지갑 (coin_wallet 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "coin_wallet")
public class CoinWallet {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  @Builder.Default
  private Integer balance = 0;

  @Column(name = "total_purchased", nullable = false)
  @Builder.Default
  private Integer totalPurchased = 0;

  @Column(name = "total_used", nullable = false)
  @Builder.Default
  private Integer totalUsed = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  /** 구매가 완료되면 코인을 충전한다. */
  public void addCoins(int amount) {
    this.balance += amount;
    this.totalPurchased += amount;
    this.updatedAt = LocalDateTime.now();
  }

  /** 슬라이드/모의고사를 사용하면 코인을 차감한다. */
  public void deductCoins(int amount) {
    this.balance -= amount;
    this.totalUsed += amount;
    this.updatedAt = LocalDateTime.now();
  }
}
