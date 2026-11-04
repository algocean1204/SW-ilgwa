/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 결제 시작 응답 DTO */
@Builder
@Schema(description = "결제 시작 응답")
public record CheckoutResponse(
    @Schema(description = "주문 ID") String orderId,
    @Schema(description = "토스페이먼츠 클라이언트 키") String tossPaymentsClientKey,
    @Schema(description = "결제 금액") Integer amount,
    @Schema(description = "주문명", example = "EZ TEAM Pro 구독") String orderName,
    @Schema(description = "고객 이메일") String customerEmail,
    @Schema(description = "고객 이름") String customerName) {}
