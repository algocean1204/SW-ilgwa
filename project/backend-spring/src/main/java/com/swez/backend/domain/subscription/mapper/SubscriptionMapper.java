/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.subscription.dto.response.CancelResponse;
import com.swez.backend.domain.subscription.dto.response.CheckoutResponse;
import com.swez.backend.domain.subscription.dto.response.ConfirmResponse;
import com.swez.backend.domain.subscription.dto.response.CurrentSubscriptionResponse;
import com.swez.backend.domain.subscription.dto.response.DowngradeResponse;
import com.swez.backend.domain.subscription.dto.response.HistoryResponse;
import com.swez.backend.domain.subscription.dto.response.PlanListResponse;
import com.swez.backend.domain.subscription.dto.response.UpgradeResponse;
import com.swez.backend.domain.subscription.entity.Payment;
import com.swez.backend.domain.subscription.entity.Subscription;
import com.swez.backend.domain.subscription.entity.SubscriptionPlan;

import lombok.extern.slf4j.Slf4j;

/** 구독 도메인 엔티티와 DTO 를 변환한다. */
@Slf4j
@Component
public class SubscriptionMapper {

  private final ObjectMapper objectMapper;

  public SubscriptionMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** SubscriptionPlan 엔티티를 PlanListResponse 로 변환한다. features JSONB 문자열을 List 로 역직렬화한다. */
  public PlanListResponse toPlanListResponse(SubscriptionPlan plan) {
    return PlanListResponse.builder()
        .code(plan.getCode())
        .priceKrw(plan.getPriceKrw())
        .periodDays(plan.getPeriodDays())
        .maxCourses(plan.getMaxCourses())
        .maxSlidesPerCourse(plan.getMaxSlidesPerCourse())
        .canUseTelegram(plan.getCanUseTelegram())
        .canCustomizeTutor(plan.getCanCustomizeTutor())
        .features(parseFeatures(plan.getFeatures()))
        .build();
  }

  /**
   * Subscription 엔티티를 CurrentSubscriptionResponse 로 변환한다. autoRenew 이 true 이면 nextBillingDate 를
   * endsAt 으로 설정한다.
   */
  public CurrentSubscriptionResponse toCurrentResponse(Subscription subscription) {
    LocalDateTime nextBillingDate =
        Boolean.TRUE.equals(subscription.getAutoRenew()) ? subscription.getEndsAt() : null;

    return CurrentSubscriptionResponse.builder()
        .subscriptionId(subscription.getId())
        .planCode(subscription.getPlan().getCode())
        .status(subscription.getStatus().getDbValue())
        .startedAt(subscription.getStartedAt())
        .endsAt(subscription.getEndsAt())
        .autoRenew(subscription.getAutoRenew())
        .nextBillingDate(nextBillingDate)
        .cancelledAt(subscription.getCancelledAt())
        .build();
  }

  /**
   * 결제 시작에 필요한 정보를 CheckoutResponse 로 조립한다.
   *
   * @param clientKey 토스페이먼츠 클라이언트 키
   */
  public CheckoutResponse toCheckoutResponse(
      String orderId,
      String clientKey,
      Integer amount,
      String orderName,
      String email,
      String name) {
    return CheckoutResponse.builder()
        .orderId(orderId)
        .tossPaymentsClientKey(clientKey)
        .amount(amount)
        .orderName(orderName)
        .customerEmail(email)
        .customerName(name)
        .build();
  }

  /** 결제 승인 후 Subscription 엔티티를 ConfirmResponse 로 변환한다. */
  public ConfirmResponse toConfirmResponse(Subscription subscription, String receiptUrl) {
    return ConfirmResponse.builder()
        .subscriptionId(subscription.getId())
        .planCode(subscription.getPlan().getCode())
        .status(subscription.getStatus().getDbValue())
        .startedAt(subscription.getStartedAt())
        .endsAt(subscription.getEndsAt())
        .receiptUrl(receiptUrl)
        .build();
  }

  /**
   * Payment 엔티티를 HistoryResponse 로 변환한다. planCode 는 payment → subscription → plan → code 경로로
   * 조회한다.
   */
  public HistoryResponse toHistoryResponse(Payment payment) {
    String planCode = null;
    if (payment.getSubscription() != null && payment.getSubscription().getPlan() != null) {
      planCode = payment.getSubscription().getPlan().getCode();
    }

    return HistoryResponse.builder()
        .paymentId(payment.getId())
        .planCode(planCode)
        .amount(payment.getAmount())
        .status(payment.getStatus().getDbValue())
        .paidAt(payment.getPaidAt())
        .tossOrderId(payment.getTossOrderId())
        .build();
  }

  /**
   * 업그레이드 결제 정보를 UpgradeResponse 로 조립한다.
   *
   * @param amount 차액(비례 계산)
   * @param clientKey 토스페이먼츠 클라이언트 키
   */
  public UpgradeResponse toUpgradeResponse(
      String orderId, Integer amount, Integer proratedDays, String clientKey) {
    return UpgradeResponse.builder()
        .orderId(orderId)
        .amount(amount)
        .proratedDays(proratedDays)
        .tossPaymentsClientKey(clientKey)
        .build();
  }

  /**
   * 다운그레이드 예약 정보를 DowngradeResponse 로 조립한다.
   *
   * @param effectiveFrom 전환 적용일
   * @param activeUntil 현재 플랜 유지 기한
   */
  public DowngradeResponse toDowngradeResponse(
      String scheduledPlanCode, LocalDateTime effectiveFrom, LocalDateTime activeUntil) {
    return DowngradeResponse.builder()
        .scheduledPlanCode(scheduledPlanCode)
        .effectiveFrom(effectiveFrom)
        .currentPlanActiveUntil(activeUntil)
        .build();
  }

  /** 구독 취소 후 Subscription 엔티티를 CancelResponse 로 변환한다. */
  public CancelResponse toCancelResponse(Subscription subscription) {
    return CancelResponse.builder()
        .subscriptionId(subscription.getId())
        .planCode(subscription.getPlan().getCode())
        .status(subscription.getStatus().getDbValue())
        .endsAt(subscription.getEndsAt())
        .cancelledAt(subscription.getCancelledAt())
        .build();
  }

  /**
   * JSONB features 문자열을 List 로 파싱한다. 파싱 실패 시 빈 리스트를 반환한다.
   *
   * @param featuresJson JSONB 문자열 (예: '["기능1","기능2"]')
   */
  private List<String> parseFeatures(String featuresJson) {
    if (featuresJson == null || featuresJson.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(featuresJson, new TypeReference<List<String>>() {});
    } catch (JsonProcessingException e) {
      log.error(
          "[SubscriptionMapper] features JSON 파싱 실패 — raw: '{}', 원인: {}",
          featuresJson,
          e.getMessage());
      return List.of();
    }
  }
}
