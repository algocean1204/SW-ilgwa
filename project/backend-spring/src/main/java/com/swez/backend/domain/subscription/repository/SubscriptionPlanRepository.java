/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.subscription.entity.SubscriptionPlan;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {

  /** 사용자에게 보여 줄 활성 플랜 목록을 조회한다. */
  List<SubscriptionPlan> findByIsActiveTrue();
}
