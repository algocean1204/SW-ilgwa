/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
import com.swez.backend.domain.subscription.service.SubscriptionService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.page.OffsetPageResponse;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 구독 요청을 SubscriptionService 에 위임한다. */
@RestController
@RequiredArgsConstructor
public class SubscriptionController implements SubscriptionApi {

  private final SubscriptionService subscriptionService;

  @Override
  public ResponseEntity<ApiResponse<List<PlanListResponse>>> getPlans() {
    return ResponseEntity.ok(ApiResponse.success(subscriptionService.getPlans()));
  }

  @Override
  public ResponseEntity<ApiResponse<CurrentSubscriptionResponse>> getCurrentSubscription(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(subscriptionService.getCurrentSubscription(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<OffsetPageResponse<HistoryResponse>>> getHistory(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            subscriptionService.getHistory(principal.getUserId(), PageRequest.of(page, size))));
  }

  @Override
  public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CheckoutRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(subscriptionService.checkout(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<ConfirmResponse>> confirm(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody ConfirmRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(subscriptionService.confirm(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<CancelResponse>> cancel(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(subscriptionService.cancel(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<UpgradeResponse>> upgrade(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody UpgradeRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(subscriptionService.upgrade(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<DowngradeResponse>> downgrade(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody DowngradeRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(subscriptionService.downgrade(principal.getUserId(), request)));
  }
}
