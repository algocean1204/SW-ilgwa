/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.swez.backend.domain.admin.dto.request.SuspendRequest;
import com.swez.backend.domain.admin.dto.request.TelegramChatIdRequest;
import com.swez.backend.domain.admin.dto.response.AdminTelegramResponse;
import com.swez.backend.domain.admin.dto.response.AdminUserListResponse;
import com.swez.backend.domain.admin.dto.response.AdminUserSuspendResponse;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.enums.UserStatus;
import com.swez.backend.global.page.OffsetPageResponse;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@Tag(name = "Admin User", description = "슈퍼 관리자 사용자 관리 API")
@RequestMapping("/admin/users")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public interface AdminUserApi {

  @Operation(summary = "관리자 사용자 목록 조회", description = "닉네임/이메일 검색, 구독 상태, 계정 상태로 필터링합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "사용자 목록 조회 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "권한 없음")
  })
  @GetMapping
  ResponseEntity<ApiResponse<OffsetPageResponse<AdminUserListResponse>>> getUsers(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) SubscriptionStatus subscriptionStatus,
      @RequestParam(required = false) UserStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);

  @Operation(
      summary = "사용자 계정 정지/해제",
      description = "ACTIVE→SUSPENDED(사유 포함), SUSPENDED→ACTIVE 상태를 토글합니다.")
  @PutMapping("/{id}/suspend")
  ResponseEntity<ApiResponse<AdminUserSuspendResponse>> toggleSuspend(
      @PathVariable String id, @RequestBody(required = false) SuspendRequest request);

  @Operation(summary = "사용자 텔레그램 chat_id 설정", description = "set/unlink/relink 작업을 수행합니다.")
  @PostMapping("/{userId}/telegram/chat-id")
  ResponseEntity<ApiResponse<AdminTelegramResponse>> updateTelegramChatId(
      @PathVariable String userId, @Valid @RequestBody TelegramChatIdRequest request);
}
