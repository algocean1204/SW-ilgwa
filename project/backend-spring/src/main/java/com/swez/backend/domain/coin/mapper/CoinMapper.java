/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.swez.backend.domain.coin.dto.response.CoinBalanceResponse;
import com.swez.backend.domain.coin.dto.response.CoinBundleResponse;
import com.swez.backend.domain.coin.dto.response.CoinCheckoutResponse;
import com.swez.backend.domain.coin.dto.response.CoinConfirmResponse;
import com.swez.backend.domain.coin.dto.response.CoinHistoryResponse;
import com.swez.backend.domain.coin.dto.response.CoinHistoryResponse.CoinHistoryItem;
import com.swez.backend.domain.coin.dto.response.CoinUseResponse;
import com.swez.backend.domain.coin.entity.CoinTransaction;
import com.swez.backend.domain.coin.entity.CoinWallet;
import com.swez.backend.domain.coin.enums.CoinBundle;

/** 코인 도메인 엔티티를 DTO로 변환한다. */
@Component
public class CoinMapper {

  /** CoinWallet 엔티티를 잔액 응답으로 변환한다. */
  public CoinBalanceResponse toBalanceResponse(CoinWallet wallet) {
    return CoinBalanceResponse.builder()
        .balance(wallet.getBalance())
        .totalPurchased(wallet.getTotalPurchased())
        .totalUsed(wallet.getTotalUsed())
        .build();
  }

  /** 지갑이 없을 때 쓰는, 모든 값이 0인 잔액 응답을 만든다. */
  public CoinBalanceResponse toEmptyBalanceResponse() {
    return CoinBalanceResponse.builder().balance(0).totalPurchased(0).totalUsed(0).build();
  }

  /** CoinTransaction 페이지를 이력 응답으로 변환한다. */
  public CoinHistoryResponse toHistoryResponse(Page<CoinTransaction> transactions) {
    return CoinHistoryResponse.builder()
        .content(transactions.getContent().stream().map(this::toHistoryItem).toList())
        .page(transactions.getNumber())
        .size(transactions.getSize())
        .totalElements(transactions.getTotalElements())
        .totalPages(transactions.getTotalPages())
        .hasNext(transactions.hasNext())
        .build();
  }

  /** CoinTransaction 엔티티를 이력 항목으로 변환한다. */
  private CoinHistoryItem toHistoryItem(CoinTransaction transaction) {
    return CoinHistoryItem.builder()
        .id(transaction.getId())
        .type(transaction.getType().getDbValue())
        .amount(transaction.getAmount())
        .balanceAfter(transaction.getBalanceAfter())
        .refId(transaction.getRefId())
        .note(transaction.getNote())
        .createdAt(transaction.getCreatedAt())
        .build();
  }

  /**
   * 코인 사용 결과를 응답으로 조립한다.
   *
   * @param coinBalance 사용 후 잔액
   */
  public CoinUseResponse toUseResponse(int coinUsed, int coinBalance, String refId) {
    return CoinUseResponse.builder()
        .coinUsed(coinUsed)
        .coinBalance(coinBalance)
        .refId(refId)
        .build();
  }

  /** CoinBundle enum을 번들 응답 DTO로 변환한다. */
  public CoinBundleResponse toBundleResponse(CoinBundle bundle) {
    return CoinBundleResponse.builder()
        .bundleId(bundle.getBundleId())
        .displayAmount(bundle.getBaseAmount())
        .bonusAmount(bundle.getBonusAmount())
        .totalAmount(bundle.getTotalAmount())
        .priceKrw(bundle.getPriceKrw())
        .build();
  }

  /** 체크아웃 정보를 응답으로 조립한다. */
  public CoinCheckoutResponse toCheckoutResponse(
      String orderId, int amount, int coinAmount, String clientKey, String orderName) {
    return CoinCheckoutResponse.builder()
        .orderId(orderId)
        .amount(amount)
        .coinAmount(coinAmount)
        .tossPaymentsClientKey(clientKey)
        .orderName(orderName)
        .build();
  }

  /** 결제 확인 결과를 응답으로 조립한다. */
  public CoinConfirmResponse toConfirmResponse(int coinAmount, int balance, String paymentKey) {
    return CoinConfirmResponse.builder()
        .coinAmount(coinAmount)
        .balance(balance)
        .paymentKey(paymentKey)
        .build();
  }
}
