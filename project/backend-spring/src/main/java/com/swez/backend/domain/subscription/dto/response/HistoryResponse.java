/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 결제 이력 응답 DTO */
@Builder
@Schema(description = "결제 이력")
public record HistoryResponse(
    @Schema(description = "결제 ID") String paymentId,
    @Schema(description = "플랜 코드") String planCode,
    @Schema(description = "결제 금액") Integer amount,
    @Schema(description = "결제 상태") String status,
    @Schema(description = "결제일") LocalDateTime paidAt,
    @Schema(description = "토스 주문 ID") String tossOrderId) {}
