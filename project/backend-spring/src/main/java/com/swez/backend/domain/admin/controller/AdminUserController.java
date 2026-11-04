/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.admin.dto.request.SuspendRequest;
import com.swez.backend.domain.admin.dto.request.TelegramChatIdRequest;
import com.swez.backend.domain.admin.dto.response.AdminTelegramResponse;
import com.swez.backend.domain.admin.dto.response.AdminUserListResponse;
import com.swez.backend.domain.admin.dto.response.AdminUserSuspendResponse;
import com.swez.backend.domain.admin.service.AdminUserService;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.enums.UserStatus;
import com.swez.backend.global.page.OffsetPageResponse;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminUserController implements AdminUserApi {

  private final AdminUserService adminUserService;

  @Override
  public ResponseEntity<ApiResponse<OffsetPageResponse<AdminUserListResponse>>> getUsers(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) SubscriptionStatus subscriptionStatus,
      @RequestParam(required = false) UserStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    return ResponseEntity.ok(
        ApiResponse.success(
            adminUserService.getUsers(keyword, subscriptionStatus, status, pageable)));
  }

  @Override
  public ResponseEntity<ApiResponse<AdminUserSuspendResponse>> toggleSuspend(
      @PathVariable String id, @RequestBody(required = false) SuspendRequest request) {
    return ResponseEntity.ok(ApiResponse.success(adminUserService.toggleSuspend(id, request)));
  }

  @Override
  public ResponseEntity<ApiResponse<AdminTelegramResponse>> updateTelegramChatId(
      @PathVariable String userId, @Valid @RequestBody TelegramChatIdRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(adminUserService.updateTelegramChatId(userId, request)));
  }
}
