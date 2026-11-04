/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 결제 승인 응답 DTO */
@Builder
@Schema(description = "결제 승인 응답")
public record ConfirmResponse(
    @Schema(description = "구독 ID") String subscriptionId,
    @Schema(description = "플랜 코드") String planCode,
    @Schema(description = "구독 상태") String status,
    @Schema(description = "구독 시작일") LocalDateTime startedAt,
    @Schema(description = "구독 만료일") LocalDateTime endsAt,
    @Schema(description = "영수증 URL") String receiptUrl) {}
