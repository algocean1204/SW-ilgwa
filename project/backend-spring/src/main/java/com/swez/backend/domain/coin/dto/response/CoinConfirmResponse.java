/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 코인 결제 승인 응답 DTO */
@Builder
@Schema(description = "코인 결제 승인 응답")
public record CoinConfirmResponse(
    @Schema(description = "충전된 코인 수량", example = "10") Integer coinAmount,
    @Schema(description = "충전 후 잔액", example = "60") Integer balance,
    @Schema(description = "토스 결제 키") String paymentKey) {}
