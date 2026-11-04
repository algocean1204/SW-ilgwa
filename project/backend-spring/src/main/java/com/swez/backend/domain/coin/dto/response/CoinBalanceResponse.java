/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 코인 잔액 조회 응답 DTO */
@Builder
@Schema(description = "코인 잔액 응답")
public record CoinBalanceResponse(
    @Schema(description = "현재 잔액", example = "50") Integer balance,
    @Schema(description = "총 구매 코인", example = "100") Integer totalPurchased,
    @Schema(description = "총 사용 코인", example = "50") Integer totalUsed) {}
