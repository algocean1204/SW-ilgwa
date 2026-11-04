/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 구독 취소 응답 DTO */
@Builder
@Schema(description = "구독 취소 응답")
public record CancelResponse(
    @Schema(description = "구독 ID") String subscriptionId,
    @Schema(description = "플랜 코드") String planCode,
    @Schema(description = "구독 상태") String status,
    @Schema(description = "만료 예정일 (이 날짜까지 이용 가능)") LocalDateTime endsAt,
    @Schema(description = "취소 처리일") LocalDateTime cancelledAt) {}
