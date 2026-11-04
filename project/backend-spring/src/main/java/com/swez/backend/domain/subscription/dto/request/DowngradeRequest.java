/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** 플랜 다운그레이드 요청 DTO */
@Schema(description = "플랜 다운그레이드 요청")
public record DowngradeRequest(
    @NotBlank @Schema(description = "다운그레이드 대상 플랜 코드", example = "free") String planCode) {}
