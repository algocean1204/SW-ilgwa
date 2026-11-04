/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.admin.dto.response.AdminDashboardResponse;
import com.swez.backend.domain.admin.service.AdminDashboardService;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminDashboardController implements AdminDashboardApi {

  private final AdminDashboardService adminDashboardService;

  @Override
  public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
    return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getDashboard()));
  }
}
