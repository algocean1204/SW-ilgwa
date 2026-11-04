/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.swez.backend.domain.admin.enums.AlertEventType;
import com.swez.backend.domain.admin.event.AlertEvent;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.exception.AuthErrorCode;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.coin.entity.CoinTransaction;
import com.swez.backend.domain.coin.entity.CoinWallet;
import com.swez.backend.domain.coin.enums.CoinTransactionType;
import com.swez.backend.domain.coin.repository.CoinTransactionRepository;
import com.swez.backend.domain.coin.repository.CoinWalletRepository;
import com.swez.backend.domain.subscription.config.TossPaymentProperties;
import com.swez.backend.domain.subscription.dto.request.CheckoutRequest;
import com.swez.backend.domain.subscription.dto.request.ConfirmRequest;
import com.swez.backend.domain.subscription.dto.request.DowngradeRequest;
import com.swez.backend.domain.subscription.dto.request.UpgradeRequest;
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
import com.swez.backend.domain.subscription.enums.PaymentStatus;
import com.swez.backend.domain.subscription.enums.SubscriptionStatusType;
import com.swez.backend.domain.subscription.exception.SubscriptionErrorCode;
import com.swez.backend.domain.subscription.mapper.SubscriptionMapper;
import com.swez.backend.domain.subscription.repository.PaymentRepository;
import com.swez.backend.domain.subscription.repository.SubscriptionPlanRepository;
import com.swez.backend.domain.subscription.repository.SubscriptionRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.page.OffsetPageResponse;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 구독 플랜 조회, 결제, 업/다운그레이드, 취소를 처리한다. */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SubscriptionService {

  private final SubscriptionPlanRepository subscriptionPlanRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final PaymentRepository paymentRepository;
  private final UserRepository userRepository;
  private final SubscriptionMapper subscriptionMapper;
  private final TossPaymentClient tossPaymentClient;
  private final TossPaymentProperties tossPaymentProperties;
  private final CoinWalletRepository coinWalletRepository;
  private final CoinTransactionRepository coinTransactionRepository;
  private final ApplicationEventPublisher eventPublisher;

  // TODO: 운영에서는 Redis로 교체
  /** 결제 대기 세션 저장소. key: orderId, value: {userId, planCode, amount} */
  private final Map<String, PendingCheckout> pendingCheckouts = new ConcurrentHashMap<>();

  /** isActive = true 인 구독 플랜 목록을 조회한다. */
  @Transactional(readOnly = true)
  public List<PlanListResponse> getPlans() {
    log.debug("[SubscriptionService] getPlans() - START");

    List<PlanListResponse> result =
        subscriptionPlanRepository.findByIsActiveTrue().stream()
            .map(subscriptionMapper::toPlanListResponse)
            .toList();

    log.debug("[SubscriptionService] getPlans() - END | count: {}", result.size());
    return result;
  }

  /** 현재 구독 상태를 조회한다. 활성 구독이 없으면 Free 기본값을 반환한다. */
  @Transactional(readOnly = true)
  public CurrentSubscriptionResponse getCurrentSubscription(String userId) {
    log.debug("[SubscriptionService] getCurrentSubscription() - START | userId: {}", userId);

    CurrentSubscriptionResponse result =
        subscriptionRepository
            .findByUserIdAndStatus(userId, SubscriptionStatusType.ACTIVE)
            .map(subscriptionMapper::toCurrentResponse)
            .orElse(
                CurrentSubscriptionResponse.builder()
                    .subscriptionId(null)
                    .planCode("free")
                    .status("active")
                    .startedAt(null)
                    .endsAt(null)
                    .autoRenew(false)
                    .nextBillingDate(null)
                    .cancelledAt(null)
                    .build());

    log.debug(
        "[SubscriptionService] getCurrentSubscription() - END | planCode: {}", result.planCode());
    return result;
  }

  /** 결제 이력을 최신 순으로 페이지네이션 조회한다. 소프트 삭제된 결제는 제외한다. */
  @Transactional(readOnly = true)
  public OffsetPageResponse<HistoryResponse> getHistory(String userId, Pageable pageable) {
    log.debug(
        "[SubscriptionService] getHistory() - START | userId: {}, page: {}, size: {}",
        userId,
        pageable.getPageNumber(),
        pageable.getPageSize());

    Page<Payment> pageResult =
        paymentRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable);

    List<HistoryResponse> content =
        pageResult.getContent().stream().map(subscriptionMapper::toHistoryResponse).toList();

    OffsetPageResponse<HistoryResponse> result =
        OffsetPageResponse.of(
            content,
            pageResult.getTotalElements(),
            pageable.getPageNumber(),
            pageable.getPageSize());

    log.debug(
        "[SubscriptionService] getHistory() - END | totalElements: {}",
        pageResult.getTotalElements());
    return result;
  }

  /**
   * 결제를 시작(체크아웃)한다. 플랜을 검증한 뒤 결제 대기 세션을 만든다.
   *
   * @return 결제 시작 응답 DTO (orderId, clientKey 등)
   */
  public CheckoutResponse checkout(String userId, CheckoutRequest request) {
    log.debug(
        "[SubscriptionService] checkout() - START | userId: {}, planCode: {}",
        userId,
        request.planCode());

    User user = findUserById(userId);

    /*
        Free 플랜은 결제할 필요가 없어 거부한다.
    */
    SubscriptionPlan plan = findPlanByCode(request.planCode());
    if (plan.getPriceKrw() == 0) {
      throw new CustomException(SubscriptionErrorCode.SUB_007);
    }

    /*
        이미 같은 플랜을 구독 중이면 거부한다.
    */
    subscriptionRepository
        .findByUserIdAndStatus(userId, SubscriptionStatusType.ACTIVE)
        .ifPresent(
            existing -> {
              if (existing.getPlan().getCode().equals(request.planCode())) {
                throw new CustomException(SubscriptionErrorCode.SUB_004);
              }
            });

    String orderId = IdGenerator.payment();
    pendingCheckouts.put(
        orderId, new PendingCheckout(userId, request.planCode(), plan.getPriceKrw()));

    /*
        응답에는 토스페이먼츠 SDK 초기화에 필요한 정보를 담는다.
    */
    String orderName =
        "EZ TEAM "
            + plan.getCode().substring(0, 1).toUpperCase()
            + plan.getCode().substring(1)
            + " 구독";
    CheckoutResponse result =
        subscriptionMapper.toCheckoutResponse(
            orderId,
            tossPaymentProperties.clientKey(),
            plan.getPriceKrw(),
            orderName,
            user.getEmail(),
            user.getNickname());

    log.debug("[SubscriptionService] checkout() - END | orderId: {}", orderId);
    return result;
  }

  /**
   * 결제를 승인한다. 토스 API 를 호출한 뒤 Subscription 과 Payment 를 만들고 사용자 구독 상태를 맞춘다.
   *
   * @param request 결제 승인 요청 DTO (paymentKey, orderId, amount)
   */
  public ConfirmResponse confirm(String userId, ConfirmRequest request) {
    log.debug(
        "[SubscriptionService] confirm() - START | userId: {}, orderId: {}",
        userId,
        request.orderId());

    /*
        결제 대기 세션에 orderId 가 없거나 userId 가 다르면 거부한다.
    */
    PendingCheckout pending = pendingCheckouts.get(request.orderId());
    if (pending == null || !pending.userId().equals(userId)) {
      throw new CustomException(SubscriptionErrorCode.SUB_003);
    }

    if (!pending.amount().equals(request.amount())) {
      throw new CustomException(SubscriptionErrorCode.SUB_005);
    }

    /*
        토스 결제 승인이 실패하면 결제 실패 알림 이벤트를 발행한 뒤 원래 예외를 다시 던진다.
        실패를 삼키지 않고 요청을 실패시킨다.
    */
    JsonNode tossResponse;
    try {
      tossResponse =
          tossPaymentClient.confirmPayment(
              request.paymentKey(), request.orderId(), request.amount());
    } catch (RuntimeException e) {
      eventPublisher.publishEvent(
          new AlertEvent(
              AlertEventType.PAYMENT_FAILED,
              "구독 결제 실패 | orderId=" + request.orderId() + ", userId=" + userId));
      throw e;
    }

    subscriptionRepository
        .findByUserIdAndStatus(userId, SubscriptionStatusType.ACTIVE)
        .ifPresent(
            existing -> {
              existing.expire();
              subscriptionRepository.save(existing);
            });

    User user = findUserById(userId);
    SubscriptionPlan plan = findPlanByCode(pending.planCode());
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime endsAt = now.plusDays(plan.getPeriodDays());

    Subscription subscription =
        Subscription.builder()
            .id(IdGenerator.subscription())
            .user(user)
            .plan(plan)
            .status(SubscriptionStatusType.ACTIVE)
            .startedAt(now)
            .endsAt(endsAt)
            .autoRenew(true)
            .build();
    subscriptionRepository.save(subscription);

    String receiptUrl =
        tossResponse != null && tossResponse.has("receipt")
            ? tossResponse.get("receipt").has("url")
                ? tossResponse.get("receipt").get("url").asText()
                : null
            : null;

    Payment payment =
        Payment.builder()
            .id(IdGenerator.payment())
            .user(user)
            .subscription(subscription)
            .amount(request.amount())
            .tossPaymentKey(request.paymentKey())
            .tossOrderId(request.orderId())
            .status(PaymentStatus.PAID)
            .paidAt(now)
            .build();
    paymentRepository.save(payment);

    user.updateSubscription(toUserSubscriptionStatus(pending.planCode()), now, endsAt);
    userRepository.save(user);

    /*
        coinGrant > 0 인 플랜만 코인을 바로 지급한다.
        충전이라 비관적 잠금 없이 지갑을 찾고, 지갑이 없으면 새로 만든다.
    */
    int coinGrant = plan.getCoinGrant();
    if (coinGrant > 0) {
      CoinWallet wallet =
          coinWalletRepository
              .findByUser_Id(userId)
              .orElseGet(
                  () -> {
                    CoinWallet newWallet =
                        CoinWallet.builder().id(IdGenerator.coinWallet()).user(user).build();
                    return coinWalletRepository.save(newWallet);
                  });
      wallet.addCoins(coinGrant);
      // 구독 지급 이력 저장
      CoinTransaction grantTransaction =
          CoinTransaction.builder()
              .id(IdGenerator.coinTransaction())
              .wallet(wallet)
              .user(user)
              .type(CoinTransactionType.SUBSCRIPTION_GRANT)
              .amount(coinGrant)
              .balanceAfter(wallet.getBalance())
              .refId(subscription.getId())
              .note(plan.getCode() + " 구독 코인 " + coinGrant + "개 지급")
              .build();
      coinTransactionRepository.save(grantTransaction);
      log.info(
          "[SubscriptionService] confirm() - 코인 지급 완료 | userId: {}, planCode: {}, coinGrant: {}",
          userId,
          plan.getCode(),
          coinGrant);
    }

    pendingCheckouts.remove(request.orderId());

    ConfirmResponse result = subscriptionMapper.toConfirmResponse(subscription, receiptUrl);

    log.debug("[SubscriptionService] confirm() - END | subscriptionId: {}", subscription.getId());
    return result;
  }

  /** 구독을 취소한다. 활성 구독의 cancel() 도메인 메서드를 호출하고 사용자 상태를 FREE 로 맞춘다. */
  public CancelResponse cancel(String userId) {
    log.debug("[SubscriptionService] cancel() - START | userId: {}", userId);

    Subscription subscription = findActiveSubscription(userId);

    subscription.cancel();
    subscriptionRepository.save(subscription);

    User user = findUserById(userId);
    user.updateSubscription(SubscriptionStatus.FREE, null, null);
    userRepository.save(user);

    CancelResponse result = subscriptionMapper.toCancelResponse(subscription);

    log.debug("[SubscriptionService] cancel() - END | subscriptionId: {}", subscription.getId());
    return result;
  }

  /** 구독을 상위 플랜으로 업그레이드한다. 비례 배분(proration) 차액을 계산해 결제 정보를 반환한다. */
  public UpgradeResponse upgrade(String userId, UpgradeRequest request) {
    log.debug(
        "[SubscriptionService] upgrade() - START | userId: {}, targetPlan: {}",
        userId,
        request.planCode());

    Subscription currentSubscription = findActiveSubscription(userId);
    SubscriptionPlan currentPlan = currentSubscription.getPlan();

    /*
        현재보다 높은 티어로만 업그레이드할 수 있다.
    */
    SubscriptionPlan targetPlan = findPlanByCode(request.planCode());
    if (planTier(targetPlan.getCode()) <= planTier(currentPlan.getCode())) {
      throw new CustomException(SubscriptionErrorCode.SUB_006);
    }

    /*
        비례 배분 차액 = ceil((targetPrice - currentPrice) * remainingDays / 30)
    */
    long remainingDays =
        ChronoUnit.DAYS.between(LocalDateTime.now(), currentSubscription.getEndsAt());
    if (remainingDays < 0) remainingDays = 0;
    int proratedAmount =
        (int)
            Math.ceil(
                (double) (targetPlan.getPriceKrw() - currentPlan.getPriceKrw())
                    * remainingDays
                    / 30);

    String orderId = IdGenerator.payment();
    pendingCheckouts.put(orderId, new PendingCheckout(userId, request.planCode(), proratedAmount));

    UpgradeResponse result =
        subscriptionMapper.toUpgradeResponse(
            orderId, proratedAmount, (int) remainingDays, tossPaymentProperties.clientKey());

    log.debug(
        "[SubscriptionService] upgrade() - END | orderId: {}, amount: {}", orderId, proratedAmount);
    return result;
  }

  /** 구독을 하위 플랜으로 다운그레이드 예약한다. 다음 결제 주기부터 적용된다. */
  public DowngradeResponse downgrade(String userId, DowngradeRequest request) {
    log.debug(
        "[SubscriptionService] downgrade() - START | userId: {}, targetPlan: {}",
        userId,
        request.planCode());

    Subscription currentSubscription = findActiveSubscription(userId);
    SubscriptionPlan currentPlan = currentSubscription.getPlan();

    /*
        현재보다 낮은 티어로만 다운그레이드할 수 있다.
    */
    SubscriptionPlan targetPlan = findPlanByCode(request.planCode());
    if (planTier(targetPlan.getCode()) >= planTier(currentPlan.getCode())) {
      throw new CustomException(SubscriptionErrorCode.SUB_006);
    }

    /*
        다운그레이드는 현재 결제 주기가 끝나는 시점부터 적용한다.
    */
    LocalDateTime effectiveFrom = currentSubscription.getEndsAt();
    LocalDateTime activeUntil = currentSubscription.getEndsAt();

    DowngradeResponse result =
        subscriptionMapper.toDowngradeResponse(request.planCode(), effectiveFrom, activeUntil);

    log.debug("[SubscriptionService] downgrade() - END | effectiveFrom: {}", effectiveFrom);
    return result;
  }

  // Private Helpers

  /** userId로 사용자를 조회한다. 없으면 USER_001 예외를 발생시킨다. */
  private User findUserById(String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(AuthErrorCode.USER_001));
  }

  /** userId로 활성 구독을 조회한다. 없으면 SUB_002 예외를 발생시킨다. */
  private Subscription findActiveSubscription(String userId) {
    return subscriptionRepository
        .findByUserIdAndStatus(userId, SubscriptionStatusType.ACTIVE)
        .orElseThrow(() -> new CustomException(SubscriptionErrorCode.SUB_002));
  }

  /** 플랜 코드로 플랜을 조회한다. 없으면 SUB_003 예외를 발생시킨다. */
  private SubscriptionPlan findPlanByCode(String code) {
    return subscriptionPlanRepository
        .findById(code)
        .orElseThrow(() -> new CustomException(SubscriptionErrorCode.SUB_003));
  }

  /** 플랜 코드를 숫자 티어로 변환한다. free=0, pro=1, premium=2 */
  private int planTier(String code) {
    return switch (code) {
      case "free" -> 0;
      case "pro" -> 1;
      case "premium" -> 2;
      default -> -1;
    };
  }

  /** 플랜 코드를 User 엔티티의 SubscriptionStatus 로 변환한다. */
  private SubscriptionStatus toUserSubscriptionStatus(String planCode) {
    return switch (planCode) {
      case "free" -> SubscriptionStatus.FREE;
      case "pro" -> SubscriptionStatus.PRO;
      case "premium" -> SubscriptionStatus.PREMIUM;
      default -> SubscriptionStatus.FREE;
    };
  }

  /** 결제 대기 세션 데이터 */
  private record PendingCheckout(String userId, String planCode, Integer amount) {}
}
