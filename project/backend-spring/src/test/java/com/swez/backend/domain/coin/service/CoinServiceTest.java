/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.coin.dto.request.CoinConfirmRequest;
import com.swez.backend.domain.coin.dto.request.CoinPurchaseRequest;
import com.swez.backend.domain.coin.dto.request.CoinUseExamRequest;
import com.swez.backend.domain.coin.dto.request.CoinUseSlideRequest;
import com.swez.backend.domain.coin.dto.response.CoinBalanceResponse;
import com.swez.backend.domain.coin.dto.response.CoinBundleResponse;
import com.swez.backend.domain.coin.dto.response.CoinCheckoutResponse;
import com.swez.backend.domain.coin.dto.response.CoinConfirmResponse;
import com.swez.backend.domain.coin.dto.response.CoinHistoryResponse;
import com.swez.backend.domain.coin.dto.response.CoinUseResponse;
import com.swez.backend.domain.coin.entity.CoinTransaction;
import com.swez.backend.domain.coin.entity.CoinWallet;
import com.swez.backend.domain.coin.enums.CoinBundle;
import com.swez.backend.domain.coin.enums.CoinTransactionType;
import com.swez.backend.domain.coin.exception.CoinErrorCode;
import com.swez.backend.domain.coin.mapper.CoinMapper;
import com.swez.backend.domain.coin.repository.CoinTransactionRepository;
import com.swez.backend.domain.coin.repository.CoinWalletRepository;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.subscription.config.TossPaymentProperties;
import com.swez.backend.domain.subscription.entity.Payment;
import com.swez.backend.domain.subscription.repository.PaymentRepository;
import com.swez.backend.domain.subscription.service.TossPaymentClient;
import com.swez.backend.global.exception.CustomException;

@DisplayName("CoinServiceTest 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoinServiceTest {

  @Mock private CoinWalletRepository coinWalletRepository;
  @Mock private CoinTransactionRepository coinTransactionRepository;
  @Mock private CoinMapper coinMapper;
  @Mock private TossPaymentClient tossPaymentClient;
  @Mock private TossPaymentProperties tossPaymentProperties;
  @Mock private PaymentRepository paymentRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private UserRepository userRepository;
  // CoinService.confirm() catch 블록의 PAYMENT_FAILED 이벤트 발행에 쓴다. 선언하지 않으면 @InjectMocks가 null을 넣어 NPE가 난다
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private CoinService coinService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String WALLET_ID = "cwl_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String COURSE_ID = "crs_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String SLIDE_ID = "sld_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String CLIENT_KEY = "test_ck_test_key";

  @Nested
  @DisplayName("getBalance()")
  class GetBalance {

    @Test
    @DisplayName("지갑이 있으면 잔액을 반환한다")
    void getBalance_walletExists_returnsBalance() {
      // given
      CoinWallet wallet = makeWallet(10);
      CoinBalanceResponse expected =
          CoinBalanceResponse.builder().balance(10).totalPurchased(20).totalUsed(10).build();

      given(coinWalletRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(wallet));
      given(coinMapper.toBalanceResponse(wallet)).willReturn(expected);

      // when
      CoinBalanceResponse result = coinService.getBalance(USER_ID);

      // then
      assertThat(result.balance()).isEqualTo(10);
    }

    @Test
    @DisplayName("지갑이 없으면 잔액 0을 반환한다")
    void getBalance_noWallet_returnsZero() {
      // given
      CoinBalanceResponse empty =
          CoinBalanceResponse.builder().balance(0).totalPurchased(0).totalUsed(0).build();

      given(coinWalletRepository.findByUser_Id(USER_ID)).willReturn(Optional.empty());
      given(coinMapper.toEmptyBalanceResponse()).willReturn(empty);

      // when
      CoinBalanceResponse result = coinService.getBalance(USER_ID);

      // then
      assertThat(result.balance()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("checkout()")
  class Checkout {

    @Test
    @DisplayName("COIN_20 체크아웃 시 보너스 포함 22개로 주문 정보를 반환한다")
    void checkout_coin20_returnsCheckoutWithBonus() {
      // given
      CoinPurchaseRequest request = new CoinPurchaseRequest("COIN_20", "http://ok", "http://fail");
      CoinCheckoutResponse expected =
          CoinCheckoutResponse.builder()
              .orderId("COIN-test")
              .amount(49900)
              .coinAmount(22)
              .tossPaymentsClientKey(CLIENT_KEY)
              .orderName("AI 코인 20개")
              .build();

      given(tossPaymentProperties.clientKey()).willReturn(CLIENT_KEY);
      given(
              coinMapper.toCheckoutResponse(
                  anyString(), eq(49900), eq(22), eq(CLIENT_KEY), eq("AI 코인 20개")))
          .willReturn(expected);

      // when
      CoinCheckoutResponse result = coinService.checkout(USER_ID, request);

      // then
      assertThat(result).isNotNull();
      assertThat(result.amount()).isEqualTo(49900);
      assertThat(result.coinAmount()).isEqualTo(22);
    }

    @Test
    @DisplayName("COIN_5 체크아웃 시 5개, 14900원으로 주문 정보를 반환한다")
    void checkout_coin5_returnsCheckoutResponse() {
      // given
      CoinPurchaseRequest request = new CoinPurchaseRequest("COIN_5", "http://ok", "http://fail");
      CoinCheckoutResponse expected =
          CoinCheckoutResponse.builder()
              .orderId("COIN-test")
              .amount(14900)
              .coinAmount(5)
              .tossPaymentsClientKey(CLIENT_KEY)
              .orderName("AI 코인 5개")
              .build();

      given(tossPaymentProperties.clientKey()).willReturn(CLIENT_KEY);
      given(
              coinMapper.toCheckoutResponse(
                  anyString(), eq(14900), eq(5), eq(CLIENT_KEY), eq("AI 코인 5개")))
          .willReturn(expected);

      // when
      CoinCheckoutResponse result = coinService.checkout(USER_ID, request);

      // then
      assertThat(result.amount()).isEqualTo(14900);
      assertThat(result.coinAmount()).isEqualTo(5);
    }

    @Test
    @DisplayName("COIN_60 체크아웃 시 보너스 포함 70개, 129900원으로 주문 정보를 반환한다")
    void checkout_coin60_returnsCheckoutWithBonus() {
      // given
      CoinPurchaseRequest request = new CoinPurchaseRequest("COIN_60", "http://ok", "http://fail");
      CoinCheckoutResponse expected =
          CoinCheckoutResponse.builder()
              .orderId("COIN-test")
              .amount(129900)
              .coinAmount(70)
              .tossPaymentsClientKey(CLIENT_KEY)
              .orderName("AI 코인 60개")
              .build();

      given(tossPaymentProperties.clientKey()).willReturn(CLIENT_KEY);
      given(
              coinMapper.toCheckoutResponse(
                  anyString(), eq(129900), eq(70), eq(CLIENT_KEY), eq("AI 코인 60개")))
          .willReturn(expected);

      // when
      CoinCheckoutResponse result = coinService.checkout(USER_ID, request);

      // then
      assertThat(result.amount()).isEqualTo(129900);
      assertThat(result.coinAmount()).isEqualTo(70);
    }

    @Test
    @DisplayName("유효하지 않은 번들 ID로 체크아웃 시 CON_007 예외를 던진다")
    void checkout_invalidBundle_throwsCon007() {
      // given
      CoinPurchaseRequest request = new CoinPurchaseRequest("INVALID", "http://ok", "http://fail");

      // when & then
      assertThatThrownBy(() -> coinService.checkout(USER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CoinErrorCode.CON_007));
    }
  }

  @Nested
  @DisplayName("confirm()")
  class Confirm {

    @Test
    @DisplayName("정상 결제 승인 시 코인이 충전된다")
    void confirm_valid_chargesCoins() {
      // given: 체크아웃으로 pendingCheckouts 에 데이터 적재
      CoinPurchaseRequest purchaseRequest =
          new CoinPurchaseRequest("COIN_20", "http://ok", "http://fail");
      given(tossPaymentProperties.clientKey()).willReturn(CLIENT_KEY);

      // Answer 로 실제 orderId 를 캡처하여 응답에 포함시킨다
      AtomicReference<String> capturedOrderId = new AtomicReference<>();
      given(
              coinMapper.toCheckoutResponse(
                  anyString(), eq(49900), eq(22), eq(CLIENT_KEY), eq("AI 코인 20개")))
          .willAnswer(
              invocation -> {
                capturedOrderId.set(invocation.getArgument(0));
                return CoinCheckoutResponse.builder()
                    .orderId(capturedOrderId.get())
                    .amount(49900)
                    .coinAmount(22)
                    .tossPaymentsClientKey(CLIENT_KEY)
                    .orderName("AI 코인 20개")
                    .build();
              });

      coinService.checkout(USER_ID, purchaseRequest);
      String orderId = capturedOrderId.get();

      // given: confirm 용 mock
      User user = makeUser();
      CoinWallet wallet = makeWallet(0);
      CoinConfirmRequest confirmRequest = new CoinConfirmRequest("pk_test", orderId, 49900);
      CoinConfirmResponse expected =
          CoinConfirmResponse.builder().coinAmount(22).balance(22).paymentKey("pk_test").build();

      given(tossPaymentClient.confirmPayment("pk_test", orderId, 49900)).willReturn(null);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(coinWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
      given(coinMapper.toConfirmResponse(22, 22, "pk_test")).willReturn(expected);

      // when
      CoinConfirmResponse result = coinService.confirm(USER_ID, confirmRequest);

      // then
      assertThat(result).isNotNull();
      assertThat(result.coinAmount()).isEqualTo(22);
      verify(paymentRepository).save(any(Payment.class));
      verify(coinTransactionRepository).save(any(CoinTransaction.class));
    }

    @Test
    @DisplayName("결제 금액 불일치 시 CON_006 예외를 던진다")
    void confirm_amountMismatch_throwsCon006() {
      // given: 체크아웃으로 pendingCheckouts 적재
      CoinPurchaseRequest purchaseRequest =
          new CoinPurchaseRequest("COIN_20", "http://ok", "http://fail");
      given(tossPaymentProperties.clientKey()).willReturn(CLIENT_KEY);

      AtomicReference<String> capturedOrderId = new AtomicReference<>();
      given(
              coinMapper.toCheckoutResponse(
                  anyString(), eq(49900), eq(22), eq(CLIENT_KEY), eq("AI 코인 20개")))
          .willAnswer(
              invocation -> {
                capturedOrderId.set(invocation.getArgument(0));
                return CoinCheckoutResponse.builder()
                    .orderId(capturedOrderId.get())
                    .amount(49900)
                    .coinAmount(22)
                    .tossPaymentsClientKey(CLIENT_KEY)
                    .orderName("AI 코인 20개")
                    .build();
              });

      coinService.checkout(USER_ID, purchaseRequest);
      String orderId = capturedOrderId.get();

      // given: 금액 불일치 요청
      CoinConfirmRequest confirmRequest = new CoinConfirmRequest("pk_test", orderId, 99999);

      // when & then
      assertThatThrownBy(() -> coinService.confirm(USER_ID, confirmRequest))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CoinErrorCode.CON_006));
    }

    @Test
    @DisplayName("토스 결제 실패 시 CON_005 예외를 던진다")
    void confirm_tossFails_throwsCon005() {
      // given: 체크아웃으로 pendingCheckouts 적재
      CoinPurchaseRequest purchaseRequest =
          new CoinPurchaseRequest("COIN_20", "http://ok", "http://fail");
      given(tossPaymentProperties.clientKey()).willReturn(CLIENT_KEY);

      AtomicReference<String> capturedOrderId = new AtomicReference<>();
      given(
              coinMapper.toCheckoutResponse(
                  anyString(), eq(49900), eq(22), eq(CLIENT_KEY), eq("AI 코인 20개")))
          .willAnswer(
              invocation -> {
                capturedOrderId.set(invocation.getArgument(0));
                return CoinCheckoutResponse.builder()
                    .orderId(capturedOrderId.get())
                    .amount(49900)
                    .coinAmount(22)
                    .tossPaymentsClientKey(CLIENT_KEY)
                    .orderName("AI 코인 20개")
                    .build();
              });

      coinService.checkout(USER_ID, purchaseRequest);
      String orderId = capturedOrderId.get();

      // given: 토스 API가 예외를 던지는 상황
      CoinConfirmRequest confirmRequest = new CoinConfirmRequest("pk_test", orderId, 49900);
      given(tossPaymentClient.confirmPayment("pk_test", orderId, 49900))
          .willThrow(new CustomException(CoinErrorCode.CON_005));

      // when & then
      assertThatThrownBy(() -> coinService.confirm(USER_ID, confirmRequest))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CoinErrorCode.CON_005));
    }
  }

  @Nested
  @DisplayName("useSlide()")
  class UseSlide {

    @Test
    @DisplayName("정상 슬라이드 해제 시 1코인이 차감된다")
    void useSlide_valid_deducts1Coin() {
      // given
      CoinUseSlideRequest request = new CoinUseSlideRequest(SLIDE_ID);
      CoinWallet wallet = makeWallet(10);
      CoinUseResponse expected =
          CoinUseResponse.builder().coinUsed(1).coinBalance(9).refId(SLIDE_ID).build();

      given(coinTransactionRepository.existsByTypeAndRefId(CoinTransactionType.USE_SLIDE, SLIDE_ID))
          .willReturn(false);
      given(coinWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
      given(coinMapper.toUseResponse(1, 9, SLIDE_ID)).willReturn(expected);

      // when
      CoinUseResponse result = coinService.useSlide(USER_ID, request);

      // then
      assertThat(result.coinUsed()).isEqualTo(1);
      assertThat(result.coinBalance()).isEqualTo(9);
      verify(coinTransactionRepository).save(any(CoinTransaction.class));
    }

    @Test
    @DisplayName("잔액 부족 시 CON_001 예외를 던진다")
    void useSlide_insufficientBalance_throwsCon001() {
      // given
      CoinUseSlideRequest request = new CoinUseSlideRequest(SLIDE_ID);
      CoinWallet wallet = makeWallet(0);

      given(coinTransactionRepository.existsByTypeAndRefId(CoinTransactionType.USE_SLIDE, SLIDE_ID))
          .willReturn(false);
      given(coinWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));

      // when & then
      assertThatThrownBy(() -> coinService.useSlide(USER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CoinErrorCode.CON_001));
    }

    @Test
    @DisplayName("이미 해제된 슬라이드면 CON_002 예외를 던진다")
    void useSlide_alreadyUnlocked_throwsCon002() {
      // given
      CoinUseSlideRequest request = new CoinUseSlideRequest(SLIDE_ID);
      given(coinTransactionRepository.existsByTypeAndRefId(CoinTransactionType.USE_SLIDE, SLIDE_ID))
          .willReturn(true);

      // when & then
      assertThatThrownBy(() -> coinService.useSlide(USER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CoinErrorCode.CON_002));
    }
  }

  @Nested
  @DisplayName("useExam()")
  class UseExam {

    @Test
    @DisplayName("코인 3개 이상이면 모의고사 생성 시 3코인이 차감된다 (완강 여부 무관)")
    void useExam_sufficientCoins_deducts3Coins() {
      // given: 미완강 코스(ACTIVE)에서도 차감된다
      CoinUseExamRequest request = new CoinUseExamRequest(COURSE_ID);
      User user = makeUser();
      Course course = makeCourse(user, CourseStatus.ACTIVE);
      CoinWallet wallet = makeWallet(10);
      CoinUseResponse expected =
          CoinUseResponse.builder().coinUsed(3).coinBalance(7).refId(COURSE_ID).build();

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(coinWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
      given(coinMapper.toUseResponse(3, 7, COURSE_ID)).willReturn(expected);

      // when
      CoinUseResponse result = coinService.useExam(USER_ID, request);

      // then
      assertThat(result.coinUsed()).isEqualTo(3);
      assertThat(result.coinBalance()).isEqualTo(7);
      verify(coinTransactionRepository).save(any(CoinTransaction.class));
    }

    @Test
    @DisplayName("완강된 코스도 3코인이 차감된다 (무료 첫 응시 로직 제거 확인)")
    void useExam_completedCourse_deducts3Coins() {
      // given
      CoinUseExamRequest request = new CoinUseExamRequest(COURSE_ID);
      User user = makeUser();
      Course course = makeCourse(user, CourseStatus.COMPLETED);
      CoinWallet wallet = makeWallet(5);
      CoinUseResponse expected =
          CoinUseResponse.builder().coinUsed(3).coinBalance(2).refId(COURSE_ID).build();

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(coinWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
      given(coinMapper.toUseResponse(3, 2, COURSE_ID)).willReturn(expected);

      // when
      CoinUseResponse result = coinService.useExam(USER_ID, request);

      // then
      assertThat(result.coinUsed()).isEqualTo(3);
      verify(coinTransactionRepository).save(any(CoinTransaction.class));
    }

    @Test
    @DisplayName("잔액이 2코인이면 CON_001 예외를 던진다 (3코인 미만)")
    void useExam_insufficientCoins_throwsCon001() {
      // given
      CoinUseExamRequest request = new CoinUseExamRequest(COURSE_ID);
      User user = makeUser();
      Course course = makeCourse(user, CourseStatus.ACTIVE);
      CoinWallet wallet = makeWallet(2); // 잔액 2 (3 미만)

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(coinWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));

      // when & then
      assertThatThrownBy(() -> coinService.useExam(USER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CoinErrorCode.CON_001));
    }

    @Test
    @DisplayName("지갑이 없으면 CON_001 예외를 던진다")
    void useExam_noWallet_throwsCon001() {
      // given
      CoinUseExamRequest request = new CoinUseExamRequest(COURSE_ID);
      User user = makeUser();
      Course course = makeCourse(user, CourseStatus.ACTIVE);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(coinWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> coinService.useExam(USER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CoinErrorCode.CON_001));
    }
  }

  @Nested
  @DisplayName("getBundles()")
  class GetBundles {

    @Test
    @DisplayName("3개 번들 목록을 반환한다")
    void getBundles_returnsAllBundles() {
      // given
      given(coinMapper.toBundleResponse(any(CoinBundle.class)))
          .willAnswer(
              invocation -> {
                CoinBundle b = invocation.getArgument(0);
                return CoinBundleResponse.builder()
                    .bundleId(b.getBundleId())
                    .displayAmount(b.getBaseAmount())
                    .bonusAmount(b.getBonusAmount())
                    .totalAmount(b.getTotalAmount())
                    .priceKrw(b.getPriceKrw())
                    .build();
              });

      // when
      List<CoinBundleResponse> result = coinService.getBundles();

      // then
      assertThat(result).hasSize(3);
      assertThat(result.get(0).bundleId()).isEqualTo("COIN_5");
      assertThat(result.get(1).bundleId()).isEqualTo("COIN_20");
      assertThat(result.get(2).bundleId()).isEqualTo("COIN_60");
    }
  }

  @Nested
  @DisplayName("getHistory()")
  class GetHistory {

    @Test
    @DisplayName("전체 이력을 페이지네이션으로 조회한다")
    void getHistory_noFilter_returnsAll() {
      // given
      Pageable pageable = PageRequest.of(0, 20);
      Page<CoinTransaction> page = new PageImpl<>(List.of(), pageable, 0);
      CoinHistoryResponse expected =
          CoinHistoryResponse.builder()
              .content(List.of())
              .page(0)
              .size(20)
              .totalElements(0L)
              .totalPages(0)
              .hasNext(false)
              .build();

      given(coinTransactionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable))
          .willReturn(page);
      given(coinMapper.toHistoryResponse(page)).willReturn(expected);

      // when
      CoinHistoryResponse result = coinService.getHistory(USER_ID, null, pageable);

      // then
      assertThat(result).isNotNull();
      assertThat(result.totalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("type 필터를 적용하여 이력을 조회한다")
    void getHistory_withFilter_returnsFiltered() {
      // given
      Pageable pageable = PageRequest.of(0, 20);
      Page<CoinTransaction> page = new PageImpl<>(List.of(), pageable, 0);
      CoinHistoryResponse expected =
          CoinHistoryResponse.builder()
              .content(List.of())
              .page(0)
              .size(20)
              .totalElements(0L)
              .totalPages(0)
              .hasNext(false)
              .build();

      given(
              coinTransactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
                  USER_ID, CoinTransactionType.PURCHASE, pageable))
          .willReturn(page);
      given(coinMapper.toHistoryResponse(page)).willReturn(expected);

      // when
      CoinHistoryResponse result = coinService.getHistory(USER_ID, "purchase", pageable);

      // then
      assertThat(result).isNotNull();
      verify(coinTransactionRepository)
          .findByUserIdAndTypeOrderByCreatedAtDesc(
              eq(USER_ID), eq(CoinTransactionType.PURCHASE), eq(pageable));
    }
  }

  // 테스트 데이터 팩토리

  private User makeUser() {
    return User.builder().id(USER_ID).email("test@example.com").nickname("테스터").build();
  }

  private CoinWallet makeWallet(int balance) {
    return CoinWallet.builder()
        .id(WALLET_ID)
        .user(makeUser())
        .balance(balance)
        .totalPurchased(20)
        .totalUsed(20 - balance)
        .build();
  }

  private Course makeCourse(User user, CourseStatus status) {
    return Course.builder().id(COURSE_ID).user(user).courseName("테스트 코스").status(status).build();
  }
}
