/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 코인 결제 준비 응답 DTO */
@Builder
@Schema(description = "코인 결제 준비 응답")
public record CoinCheckoutResponse(
    @Schema(description = "주문 ID") String orderId,
    @Schema(description = "결제 금액", example = "5900") Integer amount,
    @Schema(description = "충전될 코인 수량", example = "10") Integer coinAmount,
    @Schema(description = "토스페이먼츠 클라이언트 키") String tossPaymentsClientKey,
    @Schema(description = "주문명", example = "AI 코인 10개") String orderName) {}
