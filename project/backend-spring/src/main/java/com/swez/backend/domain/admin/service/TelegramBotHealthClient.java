/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Telegram Bot API로 헬스체크와 메시지 발송을 하는 클라이언트 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotHealthClient {

  private final RestTemplate restTemplate;

  @Value("${app.telegram.api-base-url:https://api.telegram.org}")
  private String telegramApiBaseUrl;

  public boolean getMe(String botToken) {
    try {
      /* exchange + ParameterizedTypeReference로 raw Map과 unchecked 캐스트 없이 응답을 받는다. */
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              telegramApiBaseUrl + "/bot" + botToken + "/getMe",
              HttpMethod.GET,
              null,
              new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, Object> body = response.getBody();
      return body != null && Boolean.TRUE.equals(body.get("ok"));
    } catch (RestClientException e) {
      log.warn("[TelegramBotHealthClient] getMe 실패: {}", e.getClass().getSimpleName());
      return false;
    }
  }

  /** Telegram Bot API sendMessage를 호출한다. 호출이 실패하면 예외 대신 false를 반환한다. */
  public boolean sendMessage(String botToken, Long chatId, String text) {
    try {
      String url = telegramApiBaseUrl + "/bot" + botToken + "/sendMessage";
      Map<String, Object> body = Map.of("chat_id", chatId, "text", text);
      /* exchange + ParameterizedTypeReference로 raw Map과 unchecked 캐스트 없이 응답을 받는다. */
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(body),
              new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, Object> responseBody = response.getBody();
      return responseBody != null && Boolean.TRUE.equals(responseBody.get("ok"));
    } catch (RestClientException e) {
      log.warn(
          "[TelegramBotHealthClient] sendMessage 실패 (chatId={}): {}",
          chatId,
          e.getClass().getSimpleName());
      return false;
    }
  }
}
