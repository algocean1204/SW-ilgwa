/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 온보딩 완료 응답 DTO */
@Builder
@Schema(description = "온보딩 완료 응답")
public record OnboardingResponse(
    @Schema(description = "온보딩 완료 여부 (항상 true)") boolean onboardingCompleted,
    @Schema(description = "추천 프리셋 튜터 목록") List<RecommendedTutorDto> recommendedTutors) {

  /** 추천 튜터 중첩 DTO */
  @Builder
  @Schema(description = "추천 튜터")
  public record RecommendedTutorDto(
      @Schema(description = "튜터 ID") String id,
      @Schema(description = "튜터 이름") String name,
      @Schema(description = "소개 문구") String tagline,
      @Schema(description = "프로필 이미지 URL") String profileImageUrl) {}
}
