/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.dto.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** 코인 슬라이드 사용 요청 DTO */
@Schema(description = "코인으로 슬라이드 해제 요청")
public record CoinUseSlideRequest(@NotBlank @Schema(description = "해제할 슬라이드 ID") String slideId) {}
