/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.swez.backend.domain.admin.dto.response.AdminDashboardResponse;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Dashboard", description = "슈퍼 관리자 대시보드 KPI API")
@RequestMapping("/admin/dashboard")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public interface AdminDashboardApi {

  @Operation(summary = "대시보드 KPI 조회", description = "총 사용자 수, 주간 제출률, 평균 진행률, 미응답 세션 수를 반환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "대시보드 KPI 조회 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "권한 없음")
  })
  @GetMapping
  ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard();
}
