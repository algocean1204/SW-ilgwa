/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swez.backend.domain.coin.entity.CoinWallet;

public interface CoinWalletRepository extends JpaRepository<CoinWallet, String> {

  /** 사용자 ID로 지갑 조회 */
  Optional<CoinWallet> findByUser_Id(String userId);

  /** 잔액을 바꿀 때 동시 수정을 막기 위해 비관적 잠금을 걸고 지갑을 조회한다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM CoinWallet w WHERE w.user.id = :userId")
  Optional<CoinWallet> findByUserIdForUpdate(@Param("userId") String userId);
}
