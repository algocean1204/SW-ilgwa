/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.service;

import org.springframework.stereotype.Component;

import com.swez.backend.domain.subscription.entity.Subscription;
import com.swez.backend.domain.subscription.entity.SubscriptionPlan;
import com.swez.backend.domain.subscription.enums.SubscriptionStatusType;
import com.swez.backend.domain.subscription.repository.SubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 구독 플랜 쿼터를 확인한다. 다른 도메인에서 Pro 전용 기능을 검증할 때 쓴다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionQuotaChecker {

  private final SubscriptionRepository subscriptionRepository;

  /** 현재 과외 수가 플랜 한도를 초과하는지 확인한다 */
  public boolean isMaxCoursesExceeded(String userId, int currentCourseCount) {
    SubscriptionPlan plan = getActivePlan(userId);
    if (plan == null) return currentCourseCount >= 1; // Free 기본값
    return currentCourseCount >= plan.getMaxCourses();
  }

  /** Pro 이상 플랜인지 확인한다 */
  public boolean isProOrAbove(String userId) {
    SubscriptionPlan plan = getActivePlan(userId);
    if (plan == null) return false;
    return "pro".equals(plan.getCode()) || "premium".equals(plan.getCode());
  }

  /** Premium 플랜인지 확인한다 */
  public boolean isPremium(String userId) {
    SubscriptionPlan plan = getActivePlan(userId);
    if (plan == null) return false;
    return "premium".equals(plan.getCode());
  }

  /** 활성 구독의 플랜을 조회한다. 없으면 null (Free 취급) */
  private SubscriptionPlan getActivePlan(String userId) {
    return subscriptionRepository
        .findByUserIdAndStatus(userId, SubscriptionStatusType.ACTIVE)
        .map(Subscription::getPlan)
        .orElse(null);
  }
}
