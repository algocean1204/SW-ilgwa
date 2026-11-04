/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.controller;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.admin.dto.request.AddRecipientRequest;
import com.swez.backend.domain.admin.dto.request.TelegramCredentialUpdateRequest;
import com.swez.backend.domain.admin.dto.response.RecipientListResponse;
import com.swez.backend.domain.admin.dto.response.RecipientResponse;
import com.swez.backend.domain.admin.dto.response.TelegramCredentialResponse;
import com.swez.backend.domain.admin.dto.response.TelegramCredentialUpdateResponse;
import com.swez.backend.domain.admin.dto.response.TelegramMessageListResponse;
import com.swez.backend.domain.admin.dto.response.TelegramSessionListResponse;
import com.swez.backend.domain.admin.service.AdminTelegramService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminTelegramController implements AdminTelegramApi {

  private final AdminTelegramService adminTelegramService;

  @Override
  public ResponseEntity<ApiResponse<TelegramMessageListResponse>> getMessages(
      @RequestParam Long chatId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
    return ResponseEntity.ok(
        ApiResponse.success(
            adminTelegramService.getMessages(
                chatId, from.toLocalDateTime(), to.toLocalDateTime())));
  }

  @Override
  public ResponseEntity<ApiResponse<TelegramSessionListResponse>> getSessions(
      @RequestParam String userId) {
    return ResponseEntity.ok(ApiResponse.success(adminTelegramService.getSessions(userId)));
  }

  @Override
  public ResponseEntity<ApiResponse<TelegramCredentialResponse>> getCredential(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(adminTelegramService.getCredential(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<TelegramCredentialUpdateResponse>> updateCredential(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody TelegramCredentialUpdateRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(adminTelegramService.updateCredential(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<RecipientListResponse>> getRecipients(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(adminTelegramService.getRecipients(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<RecipientResponse>> addRecipient(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody AddRecipientRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(adminTelegramService.addRecipient(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<Void>> removeRecipient(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String id) {
    adminTelegramService.removeRecipient(principal.getUserId(), id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
