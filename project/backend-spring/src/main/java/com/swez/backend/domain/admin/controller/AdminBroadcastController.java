/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.admin.dto.request.BroadcastRequest;
import com.swez.backend.domain.admin.dto.response.BroadcastResponse;
import com.swez.backend.domain.admin.service.AdminBroadcastService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 텔레그램 브로드캐스트 컨트롤러 */
@RestController
@RequiredArgsConstructor
public class AdminBroadcastController implements AdminBroadcastApi {

  private final AdminBroadcastService adminBroadcastService;

  @Override
  public ResponseEntity<ApiResponse<BroadcastResponse>> broadcast(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody BroadcastRequest request) {
    BroadcastResponse response = adminBroadcastService.broadcast(principal.getUserId(), request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
