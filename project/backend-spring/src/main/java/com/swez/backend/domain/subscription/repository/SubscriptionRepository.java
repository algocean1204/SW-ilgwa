/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.subscription.entity.Subscription;
import com.swez.backend.domain.subscription.enums.SubscriptionStatusType;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

  /** 특정 사용자의 특정 상태 구독 단건 조회. 활성 구독 확인에 쓴다. */
  Optional<Subscription> findByUserIdAndStatus(String userId, SubscriptionStatusType status);

  /** 특정 사용자의 구독 이력을 최신 순으로 조회한다. */
  List<Subscription> findByUserIdOrderByCreatedAtDesc(String userId);

  /**
   * 특정 상태이면서 종료 시각이 기준 시각 이전인 구독 목록 조회. 만료 처리 스케줄러에서 쓴다.
   *
   * @param status 조회할 구독 상태 (만료 대상은 ACTIVE)
   * @param endsAt 이 시각 이전에 종료된 구독을 만료 대상으로 본다 (보통 현재 시각)
   */
  List<Subscription> findByStatusAndEndsAtBefore(
      SubscriptionStatusType status, LocalDateTime endsAt);
}
