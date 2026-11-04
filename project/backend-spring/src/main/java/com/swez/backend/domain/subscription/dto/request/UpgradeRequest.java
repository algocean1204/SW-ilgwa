/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** 플랜 업그레이드 요청 DTO */
@Schema(description = "플랜 업그레이드 요청")
public record UpgradeRequest(
    @NotBlank @Schema(description = "업그레이드 대상 플랜 코드", example = "premium") String planCode,
    @NotBlank @Schema(description = "결제 성공 리다이렉트 URL") String successUrl,
    @NotBlank @Schema(description = "결제 실패 리다이렉트 URL") String failUrl) {}
