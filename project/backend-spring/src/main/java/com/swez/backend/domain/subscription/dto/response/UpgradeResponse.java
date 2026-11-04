/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 업그레이드 결제 응답 DTO */
@Builder
@Schema(description = "업그레이드 결제 응답")
public record UpgradeResponse(
    @Schema(description = "주문 ID") String orderId,
    @Schema(description = "차액(비례 계산)") Integer amount,
    @Schema(description = "비례 일수") Integer proratedDays,
    @Schema(description = "토스페이먼츠 클라이언트 키") String tossPaymentsClientKey) {}
