/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 코인 번들 응답 DTO */
@Builder
@Schema(description = "코인 번들 정보")
public record CoinBundleResponse(
    @Schema(description = "번들 ID", example = "COIN_20") String bundleId,
    @Schema(description = "표시 코인 수", example = "20") int displayAmount,
    @Schema(description = "보너스 코인 수", example = "2") int bonusAmount,
    @Schema(description = "실제 지급 코인 수", example = "22") int totalAmount,
    @Schema(description = "가격(원)", example = "49900") int priceKrw) {}
