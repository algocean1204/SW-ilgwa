/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.admin.enums.AlertEventType;
import com.swez.backend.domain.admin.event.AlertEvent;
import com.swez.backend.domain.subscription.entity.Subscription;
import com.swez.backend.domain.subscription.enums.SubscriptionStatusType;
import com.swez.backend.domain.subscription.repository.SubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 종료 시각이 지난 ACTIVE 구독을 EXPIRED로 전이하는 스케줄러.
 *
 * <p>구독은 endsAt 이 지나도 상태가 저절로 바뀌지 않는다. 만료 처리는 결제 갱신 경로에서만 일어나서, 갱신 없이 기간이
 * 끝난 구독은 계속 ACTIVE 로 남는다. 그래서 이 스케줄러가 주기적으로 EXPIRED 로 바꾸고 관리자에게 알린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryScheduler {

  private static final String LOG_PREFIX = "[SubscriptionExpiryScheduler]";

  private final SubscriptionRepository subscriptionRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 10분마다 종료 시각이 지난 ACTIVE 구독을 EXPIRED 로 전이하고 만료 알림 이벤트를 발행한다.
   *
   * <p>정상 갱신 경로(SubscriptionService.confirm)는 기존 ACTIVE 구독을 바로 만료 처리하므로, 이 스케줄러는 갱신 없이
   * 기간이 끝난 구독만 정리하고 정상 흐름에는 영향을 주지 않는다.
   */
  @Scheduled(fixedDelay = 10 * 60 * 1000) // 10분 간격
  @Transactional
  public void expireEndedSubscriptions() {
    LocalDateTime now = LocalDateTime.now();
    List<Subscription> expiredTargets =
        subscriptionRepository.findByStatusAndEndsAtBefore(SubscriptionStatusType.ACTIVE, now);

    if (expiredTargets.isEmpty()) {
      return;
    }

    log.warn("{} 기간 만료 구독 {}개 감지 (기준 시각: {})", LOG_PREFIX, expiredTargets.size(), now);

    for (Subscription subscription : expiredTargets) {
      String userId = subscription.getUser().getId();
      log.info(
          "{} 구독 EXPIRED 전이 — subscriptionId: {}, userId: {}, endsAt: {}",
          LOG_PREFIX,
          subscription.getId(),
          userId,
          subscription.getEndsAt());
      subscription.expire();
      subscriptionRepository.save(subscription);
      // 관리자가 만료를 알 수 있게 구독 만료 알림 이벤트를 발행한다.
      eventPublisher.publishEvent(
          new AlertEvent(AlertEventType.SUBSCRIPTION_EXPIRED, "userId=" + userId));
    }
  }
}
