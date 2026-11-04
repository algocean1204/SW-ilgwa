/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.dto.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** 코인 모의고사 사용 요청 DTO */
@Schema(description = "코인으로 모의고사 응시 요청")
public record CoinUseExamRequest(@NotBlank @Schema(description = "응시할 코스 ID") String courseId) {}
