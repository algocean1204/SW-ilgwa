/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.swez.backend.domain.admin.dto.request.BroadcastRequest;
import com.swez.backend.domain.admin.dto.response.BroadcastResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 텔레그램 브로드캐스트(일괄 발송) API 인터페이스 */
@Validated
@Tag(name = "Admin Broadcast", description = "슈퍼 관리자 텔레그램 브로드캐스트 API")
@RequestMapping("/admin")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public interface AdminBroadcastApi {

  @Operation(
      summary = "텔레그램 브로드캐스트 발송",
      description = "활성 텔레그램 연동 사용자 전체 또는 지정 사용자에게 메시지를 일괄 발송합니다.")
  @PostMapping("/broadcast")
  ResponseEntity<ApiResponse<BroadcastResponse>> broadcast(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody BroadcastRequest request);
}
