/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.controller;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.swez.backend.domain.admin.dto.request.AddRecipientRequest;
import com.swez.backend.domain.admin.dto.request.TelegramCredentialUpdateRequest;
import com.swez.backend.domain.admin.dto.response.RecipientListResponse;
import com.swez.backend.domain.admin.dto.response.RecipientResponse;
import com.swez.backend.domain.admin.dto.response.TelegramCredentialResponse;
import com.swez.backend.domain.admin.dto.response.TelegramCredentialUpdateResponse;
import com.swez.backend.domain.admin.dto.response.TelegramMessageListResponse;
import com.swez.backend.domain.admin.dto.response.TelegramSessionListResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@Tag(name = "Admin Telegram", description = "슈퍼 관리자 텔레그램 관리 API")
@RequestMapping("/admin")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public interface AdminTelegramApi {

  @Operation(summary = "텔레그램 DM 내역 조회", description = "chat_id에 연결된 사용자의 텔레그램 메시지를 조회합니다.")
  @GetMapping("/telegram/messages")
  ResponseEntity<ApiResponse<TelegramMessageListResponse>> getMessages(
      @RequestParam @NotNull Long chatId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to);

  @Operation(summary = "텔레그램 관련 세션 통합 조회", description = "소크라테스 세션과 채점 제출 세션을 통합 조회합니다.")
  @GetMapping("/telegram/sessions")
  ResponseEntity<ApiResponse<TelegramSessionListResponse>> getSessions(@RequestParam String userId);

  @Operation(summary = "텔레그램 봇 credential 조회", description = "봇 토큰은 평문 대신 fingerprint만 반환합니다.")
  @GetMapping("/telegram-credential")
  ResponseEntity<ApiResponse<TelegramCredentialResponse>> getCredential(
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(
      summary = "텔레그램 봇 credential 수정",
      description = "봇 토큰은 write-only이며 저장 후 getMe 헬스체크를 수행합니다.")
  @PatchMapping("/telegram-credential")
  ResponseEntity<ApiResponse<TelegramCredentialUpdateResponse>> updateCredential(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody TelegramCredentialUpdateRequest request);

  @Operation(summary = "텔레그램 알림 수신자 목록 조회", description = "등록된 알림 수신자 목록을 반환합니다.")
  @GetMapping("/telegram/recipients")
  ResponseEntity<ApiResponse<RecipientListResponse>> getRecipients(
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(summary = "텔레그램 알림 수신자 추가", description = "새 알림 수신자를 등록합니다.")
  @PostMapping("/telegram/recipients")
  ResponseEntity<ApiResponse<RecipientResponse>> addRecipient(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody AddRecipientRequest request);

  @Operation(summary = "텔레그램 알림 수신자 삭제", description = "등록된 수신자를 삭제합니다.")
  @DeleteMapping("/telegram/recipients/{id}")
  ResponseEntity<ApiResponse<Void>> removeRecipient(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String id);
}
