/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.swez.backend.domain.admin.dto.request.UpdateAlertRuleRequest;
import com.swez.backend.domain.admin.dto.response.AlertRuleListResponse;
import com.swez.backend.domain.admin.dto.response.AlertRuleResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 관리자 에러 알림 규칙 API */
@Validated
@Tag(name = "Admin Alert Rules", description = "관리자 에러 알림 규칙 관리 API")
@RequestMapping("/admin/alert-rules")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public interface AdminAlertRuleApi {

  @Operation(summary = "알림 규칙 목록 조회", description = "등록된 알림 규칙을 조회합니다. 규칙이 없으면 기본값을 생성합니다.")
  @GetMapping
  ResponseEntity<ApiResponse<AlertRuleListResponse>> getAlertRules(
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(summary = "알림 규칙 수정", description = "알림 규칙의 활성/비활성 및 심각도를 수정합니다.")
  @PatchMapping("/{id}")
  ResponseEntity<ApiResponse<AlertRuleResponse>> updateAlertRule(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String id,
      @RequestBody UpdateAlertRuleRequest request);
}
