/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.dto.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** 코인 구매 요청 DTO */
@Schema(description = "코인 구매 요청")
public record CoinPurchaseRequest(
    @NotBlank @Schema(description = "번들 ID", example = "bundle_10") String bundleId,
    @NotBlank @Schema(description = "결제 성공 리다이렉트 URL") String successUrl,
    @NotBlank @Schema(description = "결제 실패 리다이렉트 URL") String failUrl) {}
