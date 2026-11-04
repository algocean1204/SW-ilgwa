/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 튜터 생성 응답 DTO */
@Builder
@Schema(description = "튜터 생성 응답")
public record CreateTutorResponse(@Schema(description = "생성된 튜터 ID") String tutorId) {}
