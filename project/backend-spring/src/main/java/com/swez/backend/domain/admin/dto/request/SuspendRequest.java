/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 정지 사유를 담은 사용자 정지 요청 DTO */
@Schema(description = "사용자 정지 요청")
public record SuspendRequest(@Schema(description = "정지 사유") String reason) {}
