/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.coin.entity.CoinTransaction;
import com.swez.backend.domain.coin.enums.CoinTransactionType;

public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, String> {

  /** 사용자의 코인 이력을 최신순으로 페이지 조회한다. */
  Page<CoinTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

  /** 사용자의 코인 이력을 유형으로 거른 뒤 최신순으로 페이지 조회한다. */
  Page<CoinTransaction> findByUserIdAndTypeOrderByCreatedAtDesc(
      String userId, CoinTransactionType type, Pageable pageable);

  /** 유형과 참조 ID로 거래가 있는지 확인한다. 슬라이드 중복 해제와 무료 모의고사 확인에 쓴다. */
  boolean existsByTypeAndRefId(CoinTransactionType type, String refId);
}
