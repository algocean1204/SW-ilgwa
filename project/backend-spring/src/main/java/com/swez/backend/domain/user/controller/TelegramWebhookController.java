/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.user.service.TelegramWebhookService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 텔레그램 웹훅을 받아 secret 헤더를 검증한 뒤 서비스에 넘긴다. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/webhook")
@Tag(name = "Webhook", description = "외부 서비스 웹훅 수신 API (텔레그램 전용)")
public class TelegramWebhookController {

  @Value("${app.telegram.webhook-secret:}")
  private String webhookSecret;

  private final TelegramWebhookService telegramWebhookService;

  /** 텔레그램 Bot API 웹훅 수신 엔드포인트. 텔레그램은 항상 200 OK를 요구해 비즈니스 에러도 200으로 응답한다. */
  @Operation(
      summary = "텔레그램 웹훅 수신",
      description =
          "텔레그램 Bot API가 Update를 전송하는 엔드포인트입니다. " + "X-Telegram-Bot-Api-Secret-Token 헤더로 인증합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "웹훅 수신 성공"),
    @ApiResponse(responseCode = "403", description = "웹훅 secret 불일치")
  })
  @PostMapping("/telegram")
  public ResponseEntity<Map<String, Boolean>> handleTelegramWebhook(
      @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false)
          String secretToken,
      @RequestBody Map<String, Object> update) {

    /* 로컬 개발 편의를 위해 secret 설정값이 있을 때만 검증한다. */
    if (!webhookSecret.isBlank() && !webhookSecret.equals(secretToken)) {
      log.warn("[TelegramWebhook] secret 불일치 — 요청 거부");
      return ResponseEntity.status(403).body(Map.of("ok", false));
    }

    telegramWebhookService.handleUpdate(update);

    /* 텔레그램은 200 OK 를 요구한다. */
    return ResponseEntity.ok(Map.of("ok", true));
  }
}
