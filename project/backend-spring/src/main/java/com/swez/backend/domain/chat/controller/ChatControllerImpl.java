/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.chat.dto.request.ChatRequest;
import com.swez.backend.domain.chat.dto.response.ChatHistoryResponse;
import com.swez.backend.domain.chat.dto.response.ChatResponse;
import com.swez.backend.domain.chat.service.ChatService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** AI 채팅 요청을 ChatService에 위임한다. */
@RestController
@RequiredArgsConstructor
public class ChatControllerImpl implements ChatController {

  private final ChatService chatService;

  @Override
  public ResponseEntity<ApiResponse<ChatResponse>> chat(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @RequestBody ChatRequest request) {
    ChatResponse response = chatService.chat(principal.getUserId(), courseId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Override
  public ResponseEntity<ApiResponse<ChatHistoryResponse>> getHistory(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String courseId) {
    ChatHistoryResponse response = chatService.getHistory(principal.getUserId(), courseId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
