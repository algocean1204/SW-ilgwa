/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** 코인 결제 승인 요청 DTO */
@Schema(description = "코인 결제 승인 요청")
public record CoinConfirmRequest(
    @NotBlank @Schema(description = "토스 결제 키") String paymentKey,
    @NotBlank @Schema(description = "주문 ID") String orderId,
    @NotNull @Schema(description = "결제 금액", example = "5900") Integer amount) {}
