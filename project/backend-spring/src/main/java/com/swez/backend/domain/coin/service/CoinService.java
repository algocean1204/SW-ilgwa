/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.admin.enums.AlertEventType;
import com.swez.backend.domain.admin.event.AlertEvent;
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
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.subscription.config.TossPaymentProperties;
import com.swez.backend.domain.subscription.entity.Payment;
import com.swez.backend.domain.subscription.enums.PaymentStatus;
import com.swez.backend.domain.subscription.repository.PaymentRepository;
import com.swez.backend.domain.subscription.service.TossPaymentClient;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** AI 코인 구매·사용·이력 조회를 처리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoinService {

  private final CoinWalletRepository coinWalletRepository;
  private final CoinTransactionRepository coinTransactionRepository;
  private final CoinMapper coinMapper;
  private final TossPaymentClient tossPaymentClient;
  private final TossPaymentProperties tossPaymentProperties;
  private final PaymentRepository paymentRepository;
  private final CourseRepository courseRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  // TODO: 지금은 인스턴스 메모리에 보관한다. 운영 환경에서는 Redis로 바꾼다.
  private final ConcurrentHashMap<String, PendingCheckout> pendingCheckouts =
      new ConcurrentHashMap<>();

  /** 결제 승인 전까지 임시로 보관하는 체크아웃 대기 정보 */
  private record PendingCheckout(String userId, int amount, int coinAmount) {}

  // 비즈니스 메서드

  /** 구매 가능한 코인 번들 목록을 반환한다. */
  public List<CoinBundleResponse> getBundles() {
    return Arrays.stream(CoinBundle.values()).map(coinMapper::toBundleResponse).toList();
  }

  /**
   * 사용자의 코인 잔액을 조회한다.
   *
   * @return 잔액 응답 (지갑이 없으면 모든 값 0)
   */
  public CoinBalanceResponse getBalance(String userId) {
    return coinWalletRepository
        .findByUser_Id(userId)
        .map(coinMapper::toBalanceResponse)
        .orElseGet(coinMapper::toEmptyBalanceResponse);
  }

  /**
   * 코인 구매용 체크아웃 정보를 만든다. 토스페이먼츠 결제 화면에 필요한 데이터를 반환하며 DB에는 쓰지
   * 않는다.
   */
  @Transactional(readOnly = true)
  public CoinCheckoutResponse checkout(String userId, CoinPurchaseRequest request) {
    CoinBundle bundle = CoinBundle.fromBundleId(request.bundleId());
    int amount = bundle.getPriceKrw();
    int coinAmount = bundle.getTotalAmount();
    String orderName = bundle.getDisplayName();
    String orderId = "COIN-" + UUID.randomUUID();

    // 결제 승인 때 금액과 주문 소유자를 확인하려고 대기열에 넣는다
    pendingCheckouts.put(orderId, new PendingCheckout(userId, amount, coinAmount));

    return coinMapper.toCheckoutResponse(
        orderId, amount, coinAmount, tossPaymentProperties.clientKey(), orderName);
  }

  /**
   * 토스페이먼츠 결제를 승인하고 코인을 충전한다. 결제 금액·주문 소유자 확인 → 토스 API 호출 → 지갑 충전
   * → 이력 기록 순서로 처리한다.
   */
  @Transactional
  public CoinConfirmResponse confirm(String userId, CoinConfirmRequest request) {
    // 대기열에서 체크아웃 정보를 꺼낸다. 없으면 유효하지 않은 주문이다
    PendingCheckout pending = pendingCheckouts.remove(request.orderId());
    if (pending == null) {
      throw new CustomException(CoinErrorCode.CON_007);
    }

    if (!pending.userId().equals(userId)) {
      throw new CustomException(CoinErrorCode.CON_007);
    }

    // 체크아웃 때 금액과 다르면 위변조로 보고 거부한다
    if (!request.amount().equals(pending.amount())) {
      throw new CustomException(CoinErrorCode.CON_006);
    }

    try {
      tossPaymentClient.confirmPayment(request.paymentKey(), request.orderId(), request.amount());
    } catch (CustomException e) {
      // 실패를 삼키지 않고, 예외를 다시 던지기 전에 관리자 알림 이벤트를 발행한다
      eventPublisher.publishEvent(
          new AlertEvent(
              AlertEventType.PAYMENT_FAILED,
              "코인 결제 실패 | orderId=" + request.orderId() + ", userId=" + userId));
      throw new CustomException(CoinErrorCode.CON_005);
    }

    User user = findUserById(userId);

    CoinWallet wallet = getOrCreateWalletForUpdate(user);
    wallet.addCoins(pending.coinAmount());

    Payment payment =
        Payment.builder()
            .id(IdGenerator.payment())
            .user(user)
            // 코인 구매는 구독이 아니므로 subscription은 비워 둔다
            .amount(request.amount())
            .tossPaymentKey(request.paymentKey())
            .tossOrderId(request.orderId())
            .status(PaymentStatus.PAID)
            .paidAt(LocalDateTime.now())
            .build();
    paymentRepository.save(payment);

    CoinTransaction transaction =
        CoinTransaction.builder()
            .id(IdGenerator.coinTransaction())
            .wallet(wallet)
            .user(user)
            .type(CoinTransactionType.PURCHASE)
            .amount(pending.coinAmount())
            .balanceAfter(wallet.getBalance())
            .payment(payment)
            .note("AI 코인 " + pending.coinAmount() + "개 구매")
            .build();
    coinTransactionRepository.save(transaction);

    return coinMapper.toConfirmResponse(
        pending.coinAmount(), wallet.getBalance(), request.paymentKey());
  }

  /**
   * 코인 거래 이력을 페이지 단위로 조회한다.
   *
   * @param type 거래 유형 필터 (null이면 전체 조회)
   */
  @Transactional(readOnly = true)
  public CoinHistoryResponse getHistory(String userId, String type, Pageable pageable) {
    Page<CoinTransaction> page;

    if (type != null && !type.isBlank()) {
      // type은 DB 소문자 값이라 enum으로 바꿔서 거른다
      CoinTransactionType transactionType = CoinTransactionType.fromDbValue(type);
      page =
          coinTransactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
              userId, transactionType, pageable);
    } else {
      page = coinTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    return coinMapper.toHistoryResponse(page);
  }

  /**
   * 코인 1개로 슬라이드를 해제한다. 같은 슬라이드는 두 번 해제하지 않고, 비관적 잠금을 건 뒤 잔액을
   * 차감한다.
   */
  @Transactional
  public CoinUseResponse useSlide(String userId, CoinUseSlideRequest request) {
    boolean alreadyUnlocked =
        coinTransactionRepository.existsByTypeAndRefId(
            CoinTransactionType.USE_SLIDE, request.slideId());
    if (alreadyUnlocked) {
      throw new CustomException(CoinErrorCode.CON_002);
    }

    // 지갑이 없으면 잔액 부족(CON_001)으로 처리한다
    CoinWallet wallet =
        coinWalletRepository
            .findByUserIdForUpdate(userId)
            .orElseThrow(() -> new CustomException(CoinErrorCode.CON_001));

    if (wallet.getBalance() < 1) {
      throw new CustomException(CoinErrorCode.CON_001);
    }

    wallet.deductCoins(1);

    CoinTransaction transaction =
        CoinTransaction.builder()
            .id(IdGenerator.coinTransaction())
            .wallet(wallet)
            .user(wallet.getUser())
            .type(CoinTransactionType.USE_SLIDE)
            .amount(1)
            .balanceAfter(wallet.getBalance())
            .refId(request.slideId())
            .build();
    coinTransactionRepository.save(transaction);

    return coinMapper.toUseResponse(1, wallet.getBalance(), request.slideId());
  }

  /**
   * 코인 3개로 모의고사 생성권을 소모한다.
   *
   * <p>코스 완강(COMPLETED) 여부와 관계없이 생성할 수 있고, 무료 첫 응시 없이 생성할 때마다 코인 3개를
   * 차감한다. 코스 존재와 소유는 확인한다.
   *
   * @return 사용 결과 응답 (coinUsed=3)
   */
  @Transactional
  public CoinUseResponse useExam(String userId, CoinUseExamRequest request) {
    Course course =
        courseRepository
            .findById(request.courseId())
            .orElseThrow(() -> new CustomException(CoinErrorCode.CON_003));

    if (!course.getUser().getId().equals(userId)) {
      throw new CustomException(CoinErrorCode.CON_003);
    }

    // 지갑이 없으면 잔액 0으로 보고 잔액 부족(CON_001)으로 처리한다
    CoinWallet wallet =
        coinWalletRepository
            .findByUserIdForUpdate(userId)
            .orElseThrow(() -> new CustomException(CoinErrorCode.CON_001));

    // 모의고사 1회 생성에 코인 3개가 든다
    if (wallet.getBalance() < 3) {
      throw new CustomException(CoinErrorCode.CON_001);
    }

    wallet.deductCoins(3);

    CoinTransaction transaction =
        CoinTransaction.builder()
            .id(IdGenerator.coinTransaction())
            .wallet(wallet)
            .user(wallet.getUser())
            .type(CoinTransactionType.USE_EXAM)
            .amount(3)
            .balanceAfter(wallet.getBalance())
            .refId(request.courseId())
            .note("모의고사 생성 3코인 소모")
            .build();
    coinTransactionRepository.save(transaction);

    return coinMapper.toUseResponse(3, wallet.getBalance(), request.courseId());
  }

  // 헬퍼 메서드

  /** 사용자 ID로 User 엔티티를 조회한다. 없으면 CON_003. */
  private User findUserById(String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(CoinErrorCode.CON_003));
  }

  /**
   * 비관적 잠금을 걸고 지갑을 조회하거나, 없으면 새로 만든다. 결제 승인처럼 잔액을 바꿀 때 쓴다.
   *
   * @return 비관적 잠금이 걸린 CoinWallet
   */
  private CoinWallet getOrCreateWalletForUpdate(User user) {
    return coinWalletRepository
        .findByUserIdForUpdate(user.getId())
        .orElseGet(
            () -> {
              CoinWallet newWallet =
                  CoinWallet.builder().id(IdGenerator.coinWallet()).user(user).build();
              return coinWalletRepository.save(newWallet);
            });
  }

  /** 잠금 없이 사용자의 지갑을 조회하거나, 없으면 새로 만든다. */
  private CoinWallet getOrCreateWallet(String userId) {
    return coinWalletRepository
        .findByUser_Id(userId)
        .orElseGet(
            () -> {
              User user = findUserById(userId);
              CoinWallet newWallet =
                  CoinWallet.builder().id(IdGenerator.coinWallet()).user(user).build();
              return coinWalletRepository.save(newWallet);
            });
  }
}
