/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.admin.dto.request.UpdateAlertRuleRequest;
import com.swez.backend.domain.admin.dto.response.AlertRuleListResponse;
import com.swez.backend.domain.admin.dto.response.AlertRuleResponse;
import com.swez.backend.domain.admin.service.AdminAlertRuleService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 에러 알림 규칙 컨트롤러 */
@RestController
@RequiredArgsConstructor
public class AdminAlertRuleController implements AdminAlertRuleApi {

  private final AdminAlertRuleService adminAlertRuleService;

  @Override
  public ResponseEntity<ApiResponse<AlertRuleListResponse>> getAlertRules(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(adminAlertRuleService.getAlertRules(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<AlertRuleResponse>> updateAlertRule(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String id,
      @RequestBody UpdateAlertRuleRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            adminAlertRuleService.updateAlertRule(principal.getUserId(), id, request)));
  }
}
