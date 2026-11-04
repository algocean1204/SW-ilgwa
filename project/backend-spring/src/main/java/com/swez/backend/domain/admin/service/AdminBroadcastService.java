/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.admin.dto.request.BroadcastRequest;
import com.swez.backend.domain.admin.dto.response.BroadcastResponse;
import com.swez.backend.domain.admin.exception.AdminErrorCode;
import com.swez.backend.domain.admin.repository.AdminTelegramCredentialRepository;
import com.swez.backend.domain.user.entity.TelegramAccount;
import com.swez.backend.domain.user.repository.TelegramAccountRepository;
import com.swez.backend.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 텔레그램 브로드캐스트(일괄 발송) 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBroadcastService {

  private final AdminTelegramCredentialRepository credentialRepository;
  private final TelegramAccountRepository telegramAccountRepository;
  private final TelegramCredentialCryptoService cryptoService;
  private final TelegramBotHealthClient telegramBotHealthClient;

  /**
   * 텔레그램 브로드캐스트 메시지를 발송한다.
   *
   * @param adminUserId 봇 토큰을 조회할 관리자 ID
   * @return 발송 결과 (성공/실패 건수 및 실패 사용자 ID 목록)
   */
  public BroadcastResponse broadcast(String adminUserId, BroadcastRequest request) {
    String botToken = getBotToken(adminUserId);

    List<TelegramAccount> targets = resolveTargets(request.targetUserIds());

    return sendMessages(botToken, request.message(), targets);
  }

  /** 관리자의 봇 토큰을 조회하고 복호화한다. */
  private String getBotToken(String adminUserId) {
    return credentialRepository
        .findByUserId(adminUserId)
        .map(credential -> cryptoService.decrypt(credential.getBotTokenEncrypted()))
        .orElseThrow(() -> new CustomException(AdminErrorCode.ADMIN_006));
  }

  /** 발송 대상 텔레그램 계정 목록을 결정한다. null이면 전체 활성 계정, 아니면 지정 사용자 중 활성 계정. */
  private List<TelegramAccount> resolveTargets(List<String> targetUserIds) {
    if (targetUserIds == null || targetUserIds.isEmpty()) {
      return telegramAccountRepository.findByIsActiveTrue();
    }
    return telegramAccountRepository.findByUserIdInAndIsActiveTrue(targetUserIds);
  }

  /** 대상 계정에 순차적으로 메시지를 발송하고 결과를 집계한다. */
  private BroadcastResponse sendMessages(
      String botToken, String message, List<TelegramAccount> targets) {
    int sentCount = 0;
    List<String> failedUserIds = new ArrayList<>();

    for (TelegramAccount account : targets) {
      boolean success =
          telegramBotHealthClient.sendMessage(botToken, account.getTelegramChatId(), message);
      if (success) {
        sentCount++;
      } else {
        failedUserIds.add(account.getUser().getId());
      }
    }

    log.info(
        "[AdminBroadcastService] 브로드캐스트 완료 — 전체: {}, 성공: {}, 실패: {}",
        targets.size(),
        sentCount,
        failedUserIds.size());

    return new BroadcastResponse(sentCount, failedUserIds.size(), failedUserIds);
  }
}
