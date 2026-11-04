/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관리자 대시보드 KPI 응답 */
@Schema(description = "관리자 대시보드 KPI")
public record AdminDashboardResponse(
    @Schema(description = "활성 사용자 수") int totalUsers,
    @Schema(description = "이번 주 과제 제출률 (0.0~1.0)") double weeklySubmissionRate,
    @Schema(description = "ACTIVE 과외 평균 진행률 (0.0~100.0)") double averageProgress,
    @Schema(description = "미응답 소크라테스 세션 수") int unansweredCount) {}
