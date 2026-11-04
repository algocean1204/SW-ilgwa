/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** AI 코인 API의 Swagger 문서 인터페이스 */
@Tag(name = "Coin", description = "AI 코인 관련 API")
@RequestMapping("/coins")
public interface CoinApi {

  @Operation(summary = "코인 번들 목록 조회", description = "구매 가능한 코인 번들 목록을 반환합니다. 인증 불필요.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = CoinBundleResponse.class)))
  })
  @GetMapping("/bundles")
  ResponseEntity<ApiResponse<List<CoinBundleResponse>>> getBundles();

  @Operation(summary = "코인 잔액 조회", description = "현재 로그인한 사용자의 코인 잔액을 조회합니다. 지갑이 없으면 0을 반환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = CoinBalanceResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/balance")
  ResponseEntity<ApiResponse<CoinBalanceResponse>> getBalance(
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(
      summary = "코인 구매 체크아웃",
      description = "코인 번들 구매를 시작합니다. 번들 검증 후 토스페이먼츠 SDK 초기화에 필요한 정보를 반환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "체크아웃 성공",
        content = @Content(schema = @Schema(implementation = CoinCheckoutResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "유효하지 않은 번들 ID (CON_007)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/purchase")
  ResponseEntity<ApiResponse<CoinCheckoutResponse>> purchase(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CoinPurchaseRequest request);

  @Operation(summary = "코인 결제 승인", description = "토스페이먼츠 결제 승인을 처리합니다. 결제 검증 후 코인을 충전합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "결제 승인 성공",
        content = @Content(schema = @Schema(implementation = CoinConfirmResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "결제 금액 불일치 또는 유효하지 않은 주문 (CON_006, CON_007)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        description = "토스페이먼츠 결제 처리 실패 (CON_005)",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/confirm")
  ResponseEntity<ApiResponse<CoinConfirmResponse>> confirm(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CoinConfirmRequest request);

  @Operation(
      summary = "코인 이력 조회",
      description = "코인 사용/충전 이력을 페이지네이션으로 조회합니다. type 파라미터로 필터링할 수 있습니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = CoinHistoryResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/history")
  ResponseEntity<ApiResponse<CoinHistoryResponse>> getHistory(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(
              description = "트랜잭션 타입 필터 (purchase, use_slide, use_exam, refund)",
              required = false)
          @RequestParam(required = false)
          String type,
      @Parameter(description = "페이지 번호 (0부터 시작)", required = false)
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "페이지 크기", required = false) @RequestParam(defaultValue = "20")
          int size);

  @Operation(summary = "슬라이드 코인 사용", description = "슬라이드를 코인으로 해제합니다. 1코인이 차감됩니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "해제 성공",
        content = @Content(schema = @Schema(implementation = CoinUseResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "코인 잔액 부족 (CON_001)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "이미 해제된 슬라이드 (CON_002)",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/use/slide")
  ResponseEntity<ApiResponse<CoinUseResponse>> useSlide(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CoinUseSlideRequest request);

  @Operation(
      summary = "모의고사 코인 사용",
      description = "모의고사를 코인으로 응시합니다. 완강 후 첫 시도는 무료이며, 이후 1코인이 차감됩니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "응시 성공",
        content = @Content(schema = @Schema(implementation = CoinUseResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "코인 잔액 부족 또는 코스 미완강 (CON_001, CON_004)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "존재하지 않는 코스 (CON_003)",
        content = @Content)
  })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/use/exam")
  ResponseEntity<ApiResponse<CoinUseResponse>> useExam(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CoinUseExamRequest request);
}
