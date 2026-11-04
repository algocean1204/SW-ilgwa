/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 현재 구독 상태 응답 DTO */
@Builder
@Schema(description = "현재 구독 상태")
public record CurrentSubscriptionResponse(
    @Schema(description = "구독 ID") String subscriptionId,
    @Schema(description = "플랜 코드", example = "pro") String planCode,
    @Schema(description = "구독 상태", example = "active") String status,
    @Schema(description = "시작일") LocalDateTime startedAt,
    @Schema(description = "만료일") LocalDateTime endsAt,
    @Schema(description = "자동 갱신 여부") Boolean autoRenew,
    @Schema(description = "다음 결제 예정일") LocalDateTime nextBillingDate,
    @Schema(description = "취소일 (취소한 경우)") LocalDateTime cancelledAt) {}
