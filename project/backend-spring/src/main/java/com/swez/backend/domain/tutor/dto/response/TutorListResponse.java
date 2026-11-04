/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 튜터 목록 응답 DTO */
@Builder
@Schema(description = "튜터 목록 응답")
public record TutorListResponse(@Schema(description = "튜터 목록") List<TutorResponse> tutors) {}
