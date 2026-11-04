/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.swez.backend.domain.admin.entity.AdminTelegramCredential;
import com.swez.backend.domain.admin.repository.AdminTelegramCredentialRepository;
import com.swez.backend.domain.admin.service.TelegramCredentialCryptoService;
import com.swez.backend.domain.note.exception.NoteErrorCode;
import com.swez.backend.domain.user.entity.TelegramAccount;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.logging.LogMaskUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 텔레그램 Bot API로 노트 내용을 전송한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramNoteSender {

  private final RestTemplate restTemplate;
  private final AdminTelegramCredentialRepository credentialRepository;
  private final TelegramCredentialCryptoService cryptoService;

  @Value("${app.telegram.api-base-url:https://api.telegram.org}")
  private String telegramApiBaseUrl;

  /** 텔레그램으로 노트 메시지를 전송한다. */
  public void sendMessage(TelegramAccount telegramAccount, String messageText) {
    /* 관리자가 설정한 봇 토큰은 첫 번째 credential에서 가져온다. */
    AdminTelegramCredential credential =
        credentialRepository.findAll().stream()
            .findFirst()
            .orElseThrow(() -> new CustomException(NoteErrorCode.NTE_004));

    String botToken = cryptoService.decrypt(credential.getBotTokenEncrypted());
    String url = telegramApiBaseUrl + "/bot" + botToken + "/sendMessage";

    try {
      Map<String, Object> body =
          Map.of(
              "chat_id",
              telegramAccount.getTelegramChatId(),
              "text",
              messageText,
              "parse_mode",
              "HTML");
      restTemplate.postForObject(url, body, Map.class);
      log.info(
          "[TelegramNoteSender] 전송 성공 | chatId: {}",
          LogMaskUtil.mask(String.valueOf(telegramAccount.getTelegramChatId())));
    } catch (RestClientException e) {
      log.error(
          "[TelegramNoteSender] 전송 실패 | chatId: {}, error: {}",
          LogMaskUtil.mask(String.valueOf(telegramAccount.getTelegramChatId())),
          e.getMessage());
      throw new CustomException(NoteErrorCode.NTE_004);
    }
  }
}
