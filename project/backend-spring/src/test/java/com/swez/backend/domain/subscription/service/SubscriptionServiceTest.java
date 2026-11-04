/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.coin.entity.CoinTransaction;
import com.swez.backend.domain.coin.entity.CoinWallet;
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

@DisplayName("SubscriptionService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceTest {

  @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
  @Mock private SubscriptionRepository subscriptionRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private UserRepository userRepository;
  @Mock private TossPaymentClient tossPaymentClient;
  @Mock private TossPaymentProperties tossPaymentProperties;
  @Mock private SubscriptionMapper subscriptionMapper;
  @Mock private CoinWalletRepository coinWalletRepository;
  @Mock private CoinTransactionRepository coinTransactionRepository;

  @InjectMocks private SubscriptionService subscriptionService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String SUB_ID = "sub_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String CLIENT_KEY = "test_ck_test_key";

  @Nested
  @DisplayName("getPlans()")
  class GetPlans {

    @Test
    @DisplayName("활성 플랜 3개를 정상 반환한다")
    void getPlans_returns3Plans() {
      // given
      SubscriptionPlan free = makeFreePlan();
      SubscriptionPlan pro = makeProPlan();
      SubscriptionPlan premium = makePremiumPlan();

      given(subscriptionPlanRepository.findByIsActiveTrue())
          .willReturn(List.of(free, pro, premium));
      given(subscriptionMapper.toPlanListResponse(free))
          .willReturn(makePlanListResponse("free", 0));
      given(subscriptionMapper.toPlanListResponse(pro))
          .willReturn(makePlanListResponse("pro", 49900));
      given(subscriptionMapper.toPlanListResponse(premium))
          .willReturn(makePlanListResponse("premium", 99900));

      // when
      List<PlanListResponse> result = subscriptionService.getPlans();

      // then
      assertThat(result).hasSize(3);
      assertThat(result.get(0).code()).isEqualTo("free");
      assertThat(result.get(1).code()).isEqualTo("pro");
      assertThat(result.get(2).code()).isEqualTo("premium");
    }
  }

  @Nested
  @DisplayName("getCurrentSubscription()")
  class GetCurrentSubscription {

    @Test
    @DisplayName("활성 구독이 있으면 매핑된 응답을 반환한다")
    void getCurrentSubscription_active_returnsResponse() {
      // given
      User user = makeUser();
      SubscriptionPlan plan = makeProPlan();
      Subscription subscription = makeActiveSubscription(user, plan);

      CurrentSubscriptionResponse expected =
          CurrentSubscriptionResponse.builder()
              .subscriptionId(SUB_ID)
              .planCode("pro")
              .status("active")
              .startedAt(subscription.getStartedAt())
              .endsAt(subscription.getEndsAt())
              .autoRenew(true)
              .nextBillingDate(subscription.getEndsAt())
              .cancelledAt(null)
              .build();

      given(subscriptionRepository.findByUserIdAndStatus(USER_ID, SubscriptionStatusType.ACTIVE))
          .willReturn(Optional.of(subscription));
      given(subscriptionMapper.toCurrentResponse(subscription)).willReturn(expected);

      // when
      CurrentSubscriptionResponse result = subscriptionService.getCurrentSubscription(USER_ID);

      // then
      assertThat(result).isNotNull();
      assertThat(result.planCode()).isEqualTo("pro");
      assertThat(result.subscriptionId()).isEqualTo(SUB_ID);
      assertThat(result.autoRenew()).isTrue();
    }

    @Test
    @DisplayName("활성 구독이 없으면 Free 기본값을 반환한다")
    void getCurrentSubscription_noActive_returnsFreeDefault() {
      // given
      given(subscriptionRepository.findByUserIdAndStatus(USER_ID, SubscriptionStatusType.ACTIVE))
          .willReturn(Optional.empty());

      // when
      CurrentSubscriptionResponse result = subscriptionService.getCurrentSubscription(USER_ID);

      // then
      assertThat(result).isNotNull();
      assertThat(result.planCode()).isEqualTo("free");
      assertThat(result.subscriptionId()).isNull();
      assertThat(result.autoRenew()).isFalse();
    }
  }

  @Nested
  @DisplayName("getHistory()")
  class GetHistory {

    @Test
    @DisplayName("결제 이력을 OffsetPageResponse 로 반환한다")
    void getHistory_returnsPagedHistory() {
      // given
      User user = makeUser();
      SubscriptionPlan plan = makeProPlan();
      Subscription subscription = makeActiveSubscription(user, plan);
      Payment payment =
          Payment.builder()
              .id("pay_01ARZ3NDEKTSV4RRFFQ69G5FAV")
              .user(user)
              .subscription(subscription)
              .amount(49900)
              .tossPaymentKey("pk_test")
              .tossOrderId("ord_test")
              .status(PaymentStatus.PAID)
              .paidAt(LocalDateTime.now())
              .build();

      Pageable pageable = PageRequest.of(0, 20);
      given(paymentRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(USER_ID, pageable))
          .willReturn(new PageImpl<>(List.of(payment), pageable, 1));

      HistoryResponse historyResponse =
          HistoryResponse.builder()
              .paymentId(payment.getId())
              .planCode("pro")
              .amount(49900)
              .status("paid")
              .paidAt(payment.getPaidAt())
              .tossOrderId("ord_test")
              .build();
      given(subscriptionMapper.toHistoryResponse(payment)).willReturn(historyResponse);

      // when
      OffsetPageResponse<HistoryResponse> result =
          subscriptionService.getHistory(USER_ID, pageable);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).planCode()).isEqualTo("pro");
      assertThat(result.getTotalElements()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("checkout()")
  class Checkout {

    @Test
    @DisplayName("유효한 유료 플랜으로 체크아웃하면 CheckoutResponse 를 반환한다")
    void checkout_validPlan_returnsCheckoutResponse() {
      // given
      User user = makeUser();
      SubscriptionPlan plan = makeProPlan();
      CheckoutRequest request = new CheckoutRequest("pro", "http://ok", "http://fail");

      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(subscriptionPlanRepository.findById("pro")).willReturn(Optional.of(plan));
      given(subscriptionRepository.findByUserIdAndStatus(USER_ID, SubscriptionStatusType.ACTIVE))
          .willReturn(Optional.empty());
      given(tossPaymentProperties.clientKey()).willReturn(CLIENT_KEY);

      CheckoutResponse expected =
          CheckoutResponse.builder()
              .orderId("any-order-id")
              .tossPaymentsClientKey(CLIENT_KEY)
              .amount(49900)
              .orderName("EZ TEAM Pro 구독")
              .customerEmail("test@example.com")
              .customerName("테스터")
              .build();
      given(
              subscriptionMapper.toCheckoutResponse(
                  anyString(),
                  eq(CLIENT_KEY),
                  eq(49900),
                  anyString(),
                  eq("test@example.com"),
                  eq("테스터")))
          .willReturn(expected);

      // when
      CheckoutResponse result = subscriptionService.checkout(USER_ID, request);

      // then
      assertThat(result).isNotNull();
      assertThat(result.amount()).isEqualTo(49900);
      assertThat(result.tossPaymentsClientKey()).isEqualTo(CLIENT_KEY);
    }

    @Test
    @DisplayName("Free 플랜 체크아웃 시 SUB_007 예외를 던진다")
    void checkout_freePlan_throwsSub007() {
      // given
      User user = makeUser();
      SubscriptionPlan freePlan = makeFreePlan();
      CheckoutRequest request = new CheckoutRequest("free", "http://ok", "http://fail");

      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(subscriptionPlanRepository.findById("free")).willReturn(Optional.of(freePlan));

      // when & then
      assertThatThrownBy(() -> subscriptionService.checkout(USER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(SubscriptionErrorCode.SUB_007));
    }

    @Test
    @DisplayName("이미 동일 플랜 구독 중이면 SUB_004 예외를 던진다")
    void checkout_samePlanActive_throwsSub004() {
      // given
      User user = makeUser();
      SubscriptionPlan plan = makeProPlan();
      Subscription existing = makeActiveSubscription(user, plan);
      CheckoutRequest request = new CheckoutRequest("pro", "http://ok", "http://fail");

      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(subscriptionPlanRepository.findById("pro")).willReturn(Optional.of(plan));
      given(subscriptionRepository.findByUserIdAndStatus(USER_ID, SubscriptionStatusType.ACTIVE))
          .willReturn(Optional.of(existing));

      // when & then
      assertThatThrownBy(() -> subscriptionService.checkout(USER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(SubscriptionErrorCode.SUB_004));
    }
  }

  @Nested
  @DisplayName("confirm()")
  class Confirm {

    @Test
    @DisplayName("체크아웃 후 결제 승인하면 구독과 결제가 생성된다")
    void confirm_afterCheckout_createsSubscriptionAndPayment() {
      // given: 체크아웃으로 pendingCheckouts 에 데이터 적재
      User user = makeUser();
      SubscriptionPlan plan = makeProPlan();
      CheckoutRequest checkoutRequest = new CheckoutRequest("pro", "http://ok", "http://fail");

      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(subscriptionPlanRepository.findById("pro")).willReturn(Optional.of(plan));
      given(subscriptionRepository.findByUserIdAndStatus(USER_ID, SubscriptionStatusType.ACTIVE))
          .willReturn(Optional.empty());
      given(tossPaymentProperties.clientKey()).willReturn(CLIENT_KEY);

      // Answer 로 실제 orderId 를 캡처하여 응답에 포함시킨다
      AtomicReference<String> capturedOrderId = new AtomicReference<>();
      given(
              subscriptionMapper.toCheckoutResponse(
                  anyString(),
                  eq(CLIENT_KEY),
                  eq(49900),
                  anyString(),
                  eq("test@example.com"),
                  eq("테스터")))
          .willAnswer(
              invocation -> {
                String realOrderId = invocation.getArgument(0);
                capturedOrderId.set(realOrderId);
                return CheckoutResponse.builder()
                    .orderId(realOrderId)
                    .tossPaymentsClientKey(CLIENT_KEY)
                    .amount(49900)
                    .orderName("EZ TEAM Pro 구독")
                    .customerEmail("test@example.com")
                    .customerName("테스터")
                    .build();
              });

      subscriptionService.checkout(USER_ID, checkoutRequest);
      String orderId = capturedOrderId.get();

      // given: confirm 용 mock
      ObjectMapper objectMapper = new ObjectMapper();
      ObjectNode tossResponse = objectMapper.createObjectNode();
      ObjectNode receipt = objectMapper.createObjectNode();
      receipt.put("url", "https://receipt.example.com");
      tossResponse.set("receipt", receipt);

      given(tossPaymentClient.confirmPayment("pk_test", orderId, 49900)).willReturn(tossResponse);

      ConfirmResponse expectedConfirm =
          ConfirmResponse.builder()
              .subscriptionId("sub_generated")
              .planCode("pro")
              .status("active")
              .startedAt(LocalDateTime.now())
              .endsAt(LocalDateTime.now().plusDays(30))
              .receiptUrl("https://receipt.example.com")
              .build();
      given(
              subscriptionMapper.toConfirmResponse(
                  any(Subscription.class), eq("https://receipt.example.com")))
          .willReturn(expectedConfirm);

      // given: 코인 지급 mock: 기존 지갑 반환
      CoinWallet wallet =
          CoinWallet.builder()
              .id("cwl_01ARZ3NDEKTSV4RRFFQ69G5FAV")
              .user(user)
              .balance(0)
              .build();
      given(coinWalletRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(wallet));

      ConfirmRequest confirmRequest = new ConfirmRequest("pk_test", orderId, 49900);

      // when
      ConfirmResponse result = subscriptionService.confirm(USER_ID, confirmRequest);

      // then
      assertThat(result).isNotNull();
      assertThat(result.planCode()).isEqualTo("pro");
      assertThat(result.status()).isEqualTo("active");
      assertThat(result.receiptUrl()).isEqualTo("https://receipt.example.com");
      verify(subscriptionRepository).save(any(Subscription.class));
      verify(paymentRepository).save(any(Payment.class));
      verify(userRepository).save(any(User.class));
      // pro 플랜 coinGrant(5코인, ≈15,000원치)이 지갑에 추가되고 이력이 저장되어야 한다
      verify(coinTransactionRepository).save(any(CoinTransaction.class));
    }

    @Test
    @DisplayName("결제 금액이 불일치하면 SUB_005 예외를 던진다")
    void confirm_amountMismatch_throwsSub005() {
      // given: 체크아웃으로 pendingCheckouts 적재
      User user = makeUser();
      SubscriptionPlan plan = makeProPlan();
      CheckoutRequest checkoutRequest = new CheckoutRequest("pro", "http://ok", "http://fail");

      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(subscriptionPlanRepository.findById("pro")).willReturn(Optional.of(plan));
      given(subscriptionRepository.findByUserIdAndStatus(USER_ID, SubscriptionStatusType.ACTIVE))
          .willReturn(Optional.empty());
      given(tossPaymentProperties.clientKey()).willReturn(CLIENT_KEY);

      // Answer 로 실제 orderId 캡처
      AtomicReference<String> capturedOrderId = new AtomicReference<>();
      given(
              subscriptionMapper.toCheckoutResponse(
                  anyString(),
                  eq(CLIENT_KEY),
                  eq(49900),
                  anyString(),
                  eq("test@example.com"),
                  eq("테스터")))
          .willAnswer(
              invocation -> {
                String realOrderId = invocation.getArgument(0);
                capturedOrderId.set(realOrderId);
                return CheckoutResponse.builder()
                    .orderId(realOrderId)
                    .tossPaymentsClientKey(CLIENT_KEY)
                    .amount(49900)
                    .orderName("EZ TEAM Pro 구독")
                    .customerEmail("test@example.com")
                    .customerName("테스터")
                    .build();
              });

      subscriptionService.checkout(USER_ID, checkoutRequest);
      String orderId = capturedOrderId.get();

      // given: 금액 불일치 요청
      ConfirmRequest confirmRequest = new ConfirmRequest("pk_test", orderId, 99999);

      // when & then
      assertThatThrownBy(() -> subscriptionService.confirm(USER_ID, confirmRequest))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(SubscriptionErrorCode.SUB_005));
    }
  }

  @Nested
  @DisplayName("cancel()")
  class Cancel {

    @Test
    @DisplayName("활성 구독을 정상 취소한다")
    void cancel_activeSubscription_cancelsSuccessfully() {
      // given
      User user = makeUser();
      SubscriptionPlan plan = makeProPlan();
      Subscription subscription = makeActiveSubscription(user, plan);

      given(subscriptionRepository.findByUserIdAndStatus(USER_ID, SubscriptionStatusType.ACTIVE))
          .willReturn(Optional.of(subscription));
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

      CancelResponse expected =
          CancelResponse.builder()
              .subscriptionId(SUB_ID)
              .planCode("pro")
              .status("cancelled")
              .endsAt(subscription.getEndsAt())
              .cancelledAt(LocalDateTime.now())
              .build();
      given(subscriptionMapper.toCancelResponse(subscription)).willReturn(expected);

      // when
      CancelResponse result = subscriptionService.cancel(USER_ID);

      // then
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo("cancelled");
      assertThat(result.subscriptionId()).isEqualTo(SUB_ID);
      verify(subscriptionRepository).save(subscription);
      verify(userRepository).save(user);
    }

    @Test
    @DisplayName("활성 구독이 없으면 SUB_002 예외를 던진다")
    void cancel_noActiveSubscription_throwsSub002() {
      // given
      given(subscriptionRepository.findByUserIdAndStatus(USER_ID, SubscriptionStatusType.ACTIVE))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> subscriptionService.cancel(USER_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(SubscriptionErrorCode.SUB_002));
    }
  }

  @Nested
  @DisplayName("upgrade()")
  class Upgrade {

    @Test
    @DisplayName("Pro 에서 Premium 으로 업그레이드하면 비례 배분 금액을 반환한다")
    void upgrade_proToPremium_returnsProratedAmount() {
      // given
      User user = makeUser();
      SubscriptionPlan proPlan = makeProPlan();
      SubscriptionPlan premiumPlan = makePremiumPlan();
      Subscription subscription = makeActiveSubscription(user, proPlan);

      given(subscriptionRepository.findByUserIdAndStatus(USER_ID, SubscriptionStatusType.ACTIVE))
          .willReturn(Optional.of(subscription));
      given(subscriptionPlanRepository.findById("premium")).willReturn(Optional.of(premiumPlan));
      given(tossPaymentProperties.clientKey()).willReturn(CLIENT_KEY);

      UpgradeResponse expected =
          UpgradeResponse.builder()
              .orderId("ord_upgrade")
              .amount(33333)
              .proratedDays(20)
              .tossPaymentsClientKey(CLIENT_KEY)
              .build();
      given(subscriptionMapper.toUpgradeResponse(anyString(), anyInt(), anyInt(), eq(CLIENT_KEY)))
          .willReturn(expected);

      UpgradeRequest request = new UpgradeRequest("premium", "http://ok", "http://fail");

      // when
      UpgradeResponse result = subscriptionService.upgrade(USER_ID, request);

      // then
      assertThat(result).isNotNull();
      assertThat(result.tossPaymentsClientKey()).isEqualTo(CLIENT_KEY);
    }

    @Test
    @DisplayName("동일 또는 하위 플랜으로 업그레이드 시 SUB_006 예외를 던진다")
    void upgrade_sameOrLowerTier_throwsSub006() {
      // given
      User user = makeUser();
      SubscriptionPlan proPlan = makeProPlan();
      SubscriptionPlan freePlan = makeFreePlan();
      Subscription subscription = makeActiveSubscription(user, proPlan);

      given(subscriptionRepository.findByUserIdAndStatus(USER_ID, SubscriptionStatusType.ACTIVE))
          .willReturn(Optional.of(subscription));
      given(subscriptionPlanRepository.findById("free")).willReturn(Optional.of(freePlan));

      UpgradeRequest request = new UpgradeRequest("free", "http://ok", "http://fail");

      // when & then
      assertThatThrownBy(() -> subscriptionService.upgrade(USER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(SubscriptionErrorCode.SUB_006));
    }
  }

  @Nested
  @DisplayName("downgrade()")
  class Downgrade {

    @Test
    @DisplayName("Premium 에서 Pro 로 다운그레이드를 예약한다")
    void downgrade_premiumToPro_schedulesDowngrade() {
      // given
      User user = makeUser();
      SubscriptionPlan premiumPlan = makePremiumPlan();
      SubscriptionPlan proPlan = makeProPlan();
      Subscription subscription = makeActiveSubscription(user, premiumPlan);

      given(subscriptionRepository.findByUserIdAndStatus(USER_ID, SubscriptionStatusType.ACTIVE))
          .willReturn(Optional.of(subscription));
      given(subscriptionPlanRepository.findById("pro")).willReturn(Optional.of(proPlan));

      DowngradeResponse expected =
          DowngradeResponse.builder()
              .scheduledPlanCode("pro")
              .effectiveFrom(subscription.getEndsAt())
              .currentPlanActiveUntil(subscription.getEndsAt())
              .build();
      given(
              subscriptionMapper.toDowngradeResponse(
                  eq("pro"), any(LocalDateTime.class), any(LocalDateTime.class)))
          .willReturn(expected);

      DowngradeRequest request = new DowngradeRequest("pro");

      // when
      DowngradeResponse result = subscriptionService.downgrade(USER_ID, request);

      // then
      assertThat(result).isNotNull();
      assertThat(result.scheduledPlanCode()).isEqualTo("pro");
      assertThat(result.effectiveFrom()).isEqualTo(subscription.getEndsAt());
    }

    @Test
    @DisplayName("동일 또는 상위 플랜으로 다운그레이드 시 SUB_006 예외를 던진다")
    void downgrade_sameOrHigherTier_throwsSub006() {
      // given
      User user = makeUser();
      SubscriptionPlan proPlan = makeProPlan();
      SubscriptionPlan premiumPlan = makePremiumPlan();
      Subscription subscription = makeActiveSubscription(user, proPlan);

      given(subscriptionRepository.findByUserIdAndStatus(USER_ID, SubscriptionStatusType.ACTIVE))
          .willReturn(Optional.of(subscription));
      given(subscriptionPlanRepository.findById("premium")).willReturn(Optional.of(premiumPlan));

      DowngradeRequest request = new DowngradeRequest("premium");

      // when & then
      assertThatThrownBy(() -> subscriptionService.downgrade(USER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(SubscriptionErrorCode.SUB_006));
    }
  }

  // 테스트 데이터 팩토리

  private User makeUser() {
    return User.builder()
        .id(USER_ID)
        .email("test@example.com")
        .nickname("테스터")
        .subscriptionStatus(SubscriptionStatus.FREE)
        .build();
  }

  private SubscriptionPlan makeFreePlan() {
    return SubscriptionPlan.builder()
        .code("free")
        .priceKrw(0)
        .periodDays(30)
        .maxCourses(1)
        .canUseTelegram(false)
        .canCustomizeTutor(false)
        .coinGrant(0)
        .isActive(true)
        .build();
  }

  private SubscriptionPlan makeProPlan() {
    return SubscriptionPlan.builder()
        .code("pro")
        .priceKrw(49900)
        .periodDays(30)
        .maxCourses(3)
        .canUseTelegram(true)
        .canCustomizeTutor(true)
        .coinGrant(5)
        .isActive(true)
        .build();
  }

  private SubscriptionPlan makePremiumPlan() {
    return SubscriptionPlan.builder()
        .code("premium")
        .priceKrw(99900)
        .periodDays(30)
        .maxCourses(8)
        .canUseTelegram(true)
        .canCustomizeTutor(true)
        .coinGrant(22)
        .isActive(true)
        .build();
  }

  private Subscription makeActiveSubscription(User user, SubscriptionPlan plan) {
    return Subscription.builder()
        .id(SUB_ID)
        .user(user)
        .plan(plan)
        .status(SubscriptionStatusType.ACTIVE)
        .startedAt(LocalDateTime.now().minusDays(10))
        .endsAt(LocalDateTime.now().plusDays(20))
        .autoRenew(true)
        .build();
  }

  private PlanListResponse makePlanListResponse(String code, int priceKrw) {
    return PlanListResponse.builder()
        .code(code)
        .priceKrw(priceKrw)
        .periodDays(30)
        .maxCourses(code.equals("free") ? 1 : code.equals("pro") ? 3 : 8)
        .maxSlidesPerCourse(null)
        .canUseTelegram(!code.equals("free"))
        .canCustomizeTutor(!code.equals("free"))
        .features(List.of())
        .build();
  }
}
