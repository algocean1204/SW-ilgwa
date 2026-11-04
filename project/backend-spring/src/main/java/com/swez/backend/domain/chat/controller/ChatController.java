/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.chat.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.swez.backend.domain.chat.dto.request.ChatRequest;
import com.swez.backend.domain.chat.dto.response.ChatHistoryResponse;
import com.swez.backend.domain.chat.dto.response.ChatResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** AI 채팅 질문 전송과 히스토리 조회 API */
@Tag(name = "Chat", description = "AI 채팅 API")
@RequestMapping("/tutoring/{courseId}")
@SecurityRequirement(name = "bearerAuth")
public interface ChatController {

  @Operation(summary = "AI 채팅", description = "AI 튜터에게 질문합니다. DB 컨텍스트를 조회한 뒤 FastAPI로 전달합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "채팅 성공",
        content = @Content(schema = @Schema(implementation = ChatResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CHAT_002)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        description = "AI 응답 실패 (CHAT_001)",
        content = @Content)
  })
  @PostMapping("/chat")
  ResponseEntity<ApiResponse<ChatResponse>> chat(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Valid @RequestBody ChatRequest request);

  @Operation(summary = "채팅 히스토리 조회", description = "과외의 채팅 히스토리를 최신순으로 조회합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = ChatHistoryResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CHAT_002)",
        content = @Content)
  })
  @GetMapping("/chat/history")
  ResponseEntity<ApiResponse<ChatHistoryResponse>> getHistory(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId);
}
