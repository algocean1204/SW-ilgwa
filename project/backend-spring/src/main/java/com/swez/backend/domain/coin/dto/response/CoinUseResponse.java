/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 코인 사용 응답 DTO */
@Builder
@Schema(description = "코인 사용 응답")
public record CoinUseResponse(
    @Schema(description = "사용한 코인 수량", example = "1") Integer coinUsed,
    @Schema(description = "사용 후 잔액", example = "49") Integer coinBalance,
    @Schema(description = "참조 대상 ID") String refId) {}
