/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
import com.swez.backend.domain.coin.service.CoinService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 코인 요청을 CoinService에 위임한다. */
@RestController
@RequiredArgsConstructor
public class CoinController implements CoinApi {

  private final CoinService coinService;

  @Override
  public ResponseEntity<ApiResponse<List<CoinBundleResponse>>> getBundles() {
    return ResponseEntity.ok(ApiResponse.success(coinService.getBundles()));
  }

  @Override
  public ResponseEntity<ApiResponse<CoinBalanceResponse>> getBalance(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(ApiResponse.success(coinService.getBalance(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<CoinCheckoutResponse>> purchase(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CoinPurchaseRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(coinService.checkout(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<CoinConfirmResponse>> confirm(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CoinConfirmRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(coinService.confirm(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<CoinHistoryResponse>> getHistory(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            coinService.getHistory(principal.getUserId(), type, PageRequest.of(page, size))));
  }

  @Override
  public ResponseEntity<ApiResponse<CoinUseResponse>> useSlide(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CoinUseSlideRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(coinService.useSlide(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<CoinUseResponse>> useExam(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CoinUseExamRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(coinService.useExam(principal.getUserId(), request)));
  }
}
