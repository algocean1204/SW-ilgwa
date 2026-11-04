/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.page.OffsetPageResponse;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 구독/결제 API 의 Swagger 문서 인터페이스. */
@Tag(name = "Subscription", description = "구독/결제 관련 API")
@RequestMapping("/subscription")
public interface SubscriptionApi {

  @Operation(summary = "구독 플랜 목록 조회", description = "활성화된 구독 플랜 목록을 조회합니다. 인증 없이 접근 가능합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = PlanListResponse.class))),
  })
  @GetMapping("/plans")
  ResponseEntity<ApiResponse<List<PlanListResponse>>> getPlans();

  @Operation(
      summary = "현재 구독 상태 조회",
      description = "로그인한 사용자의 현재 구독 상태를 조회합니다. 활성 구독이 없으면 Free 기본값을 반환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = CurrentSubscriptionResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/current")
  ResponseEntity<ApiResponse<CurrentSubscriptionResponse>> getCurrentSubscription(
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(
      summary = "결제 이력 조회",
      description = "로그인한 사용자의 결제 이력을 페이지네이션으로 조회합니다. 소프트 삭제된 결제는 제외됩니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = HistoryResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/history")
  ResponseEntity<ApiResponse<OffsetPageResponse<HistoryResponse>>> getHistory(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "페이지 번호 (0부터 시작)", required = false)
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "페이지 크기", required = false) @RequestParam(defaultValue = "20")
          int size);

  @Operation(
      summary = "결제 시작 (체크아웃)",
      description = "구독 결제를 시작합니다. 플랜 검증 후 토스페이먼츠 SDK 초기화에 필요한 정보를 반환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "체크아웃 성공",
        content = @Content(schema = @Schema(implementation = CheckoutResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "유효하지 않은 플랜 또는 Free 플랜 결제 시도 (SUB_003, SUB_007)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "이미 해당 플랜 구독 중 (SUB_004)",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/checkout")
  ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CheckoutRequest request);

  @Operation(
      summary = "결제 승인",
      description = "토스페이먼츠 결제 승인을 처리합니다. 결제 대기 세션 검증 후 구독 및 결제 레코드를 생성합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "결제 승인 성공",
        content = @Content(schema = @Schema(implementation = ConfirmResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "유효하지 않은 주문 또는 금액 불일치 (SUB_003, SUB_005)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        description = "토스페이먼츠 결제 처리 실패 (SUB_001)",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/confirm")
  ResponseEntity<ApiResponse<ConfirmResponse>> confirm(
      @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ConfirmRequest request);

  @Operation(summary = "구독 취소", description = "현재 활성 구독을 취소합니다. 취소 즉시 상태가 변경되며 자동갱신이 해제됩니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "취소 성공",
        content = @Content(schema = @Schema(implementation = CancelResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "활성 구독 없음 (SUB_002)",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @DeleteMapping("/cancel")
  ResponseEntity<ApiResponse<CancelResponse>> cancel(
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(
      summary = "플랜 업그레이드",
      description = "상위 플랜으로 업그레이드합니다. 남은 기간에 대한 비례 배분(proration) 차액을 계산하여 결제 정보를 반환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "업그레이드 결제 정보 반환",
        content = @Content(schema = @Schema(implementation = UpgradeResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "현재 플랜 이하로는 업그레이드 불가 (SUB_006)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "활성 구독 없음 (SUB_002)",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/upgrade")
  ResponseEntity<ApiResponse<UpgradeResponse>> upgrade(
      @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody UpgradeRequest request);

  @Operation(summary = "플랜 다운그레이드 예약", description = "하위 플랜으로 다운그레이드를 예약합니다. 현재 결제 주기 만료 후 적용됩니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "다운그레이드 예약 성공",
        content = @Content(schema = @Schema(implementation = DowngradeResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "현재 플랜 이상으로는 다운그레이드 불가 (SUB_006)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "활성 구독 없음 (SUB_002)",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/downgrade")
  ResponseEntity<ApiResponse<DowngradeResponse>> downgrade(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody DowngradeRequest request);
}
