/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 구독 플랜 목록 아이템 응답 DTO */
@Builder
@Schema(description = "구독 플랜 정보")
public record PlanListResponse(
    @Schema(description = "플랜 코드", example = "pro") String code,
    @Schema(description = "월 요금(원)", example = "14900") Integer priceKrw,
    @Schema(description = "기간(일)", example = "30") Integer periodDays,
    @Schema(description = "최대 과외 수", example = "2") Integer maxCourses,
    @Schema(description = "과외당 최대 슬라이드 수 (null=무제한)") Integer maxSlidesPerCourse,
    @Schema(description = "텔레그램 봇 사용 가능") Boolean canUseTelegram,
    @Schema(description = "AI 튜터 커스터마이징 가능") Boolean canCustomizeTutor,
    @Schema(description = "기능 목록") List<String> features) {}
