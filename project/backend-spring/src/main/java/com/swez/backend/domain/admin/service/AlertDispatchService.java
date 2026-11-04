/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.admin.entity.AdminAlertRule;
import com.swez.backend.domain.admin.entity.AdminTelegramCredential;
import com.swez.backend.domain.admin.entity.AdminTelegramRecipient;
import com.swez.backend.domain.admin.event.AlertEvent;
import com.swez.backend.domain.admin.repository.AdminAlertRuleRepository;
import com.swez.backend.domain.admin.repository.AdminTelegramRecipientRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AlertEvent를 받아 활성 규칙 기준으로 관리자에게 텔레그램 알림을 발송한다.
 *
 * <p>알림은 부가 기능이라 발송 실패가 결제·구독 흐름을 막으면 안 된다. 그래서 @Async 별도 스레드에서 처리하고,
 * 발송 중 예외는 모두 잡아 로그만 남기며 리스너 밖으로 던지지 않는다(best-effort).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDispatchService {

  private static final String LOG_PREFIX = "[AlertDispatchService]";

  private final AdminAlertRuleRepository alertRuleRepository;
  private final AdminTelegramRecipientRepository recipientRepository;
  private final TelegramCredentialCryptoService cryptoService;
  private final TelegramBotHealthClient telegramBotHealthClient;

  /**
   * AlertEvent를 수신해 해당 이벤트 유형의 활성 규칙별로 텔레그램 알림을 발송한다.
   *
   * <p>@Async는 새 스레드에서 실행되어 호출 측 트랜잭션을 물려받지 못한다. 이 스레드에서 트랜잭션을 따로 열어야
   * EntityGraph 즉시 로딩과 수신자 조회가 같은 세션에서 동작한다. 읽기만 하므로 readOnly = true로 둔다.
   */
  @Async
  @EventListener
  @Transactional(readOnly = true)
  public void onAlertEvent(AlertEvent event) {
    log.info("{} onAlertEvent() - START | eventType: {}", LOG_PREFIX, event.eventType());

    /*
        enabled = true 규칙만 조회되므로 발송 여부는 활성 상태로만 정해진다.
        심각도는 발송 필터가 아니라 메시지 prefix로만 쓴다(심각도 임계치 필터링 없음).
    */
    List<AdminAlertRule> rules =
        alertRuleRepository.findByEventTypeAndEnabledTrue(event.eventType());
    if (rules.isEmpty()) {
      log.info("{} onAlertEvent() - END | 활성 규칙 없음 | eventType: {}", LOG_PREFIX, event.eventType());
      return;
    }

    /* credential 하나의 발송 실패가 다른 규칙 발송을 막지 않도록 규칙 단위로 try/catch 한다. */
    for (AdminAlertRule rule : rules) {
      dispatchForRule(rule, event);
    }

    log.info(
        "{} onAlertEvent() - END | eventType: {}, 규칙 수: {}",
        LOG_PREFIX,
        event.eventType(),
        rules.size());
  }

  /**
   * 단일 규칙의 알림 메시지를 만들어 관리자 chat_id와 수신자 전원에게 발송한다.
   *
   * <p>봇 토큰이 잘못됐거나 복호화에 실패한 credential 하나 때문에 나머지 규칙 발송이 멈추면 안 된다. 그래서 모든
   * 예외를 여기서 잡아 로그만 남긴다.
   *
   * @param rule 발송 기준 규칙 (credential·심각도 포함)
   */
  private void dispatchForRule(AdminAlertRule rule, AlertEvent event) {
    try {
      /* EntityGraph로 즉시 로딩된 credential에서 암호문을 꺼내 복호화한다. */
      AdminTelegramCredential credential = rule.getCredential();
      String botToken = cryptoService.decrypt(credential.getBotTokenEncrypted());

      String message = "[" + rule.getSeverity() + "] " + event.eventType() + " — " + event.detail();

      /*
          관리자 chat_id와 수신자 chat_id를 LinkedHashSet으로 합쳐 중복을 없앤다.
          관리자가 수신자로도 등록돼 있거나 수신자끼리 겹쳐도 채팅방마다 한 번만 보낸다.
      */
      Set<Long> targetChatIds = new LinkedHashSet<>();
      Long adminChatId = credential.getAdminChatId();
      if (adminChatId != null) {
        targetChatIds.add(adminChatId);
      }
      recipientRepository.findByCredentialId(credential.getId()).stream()
          .map(AdminTelegramRecipient::getChatId)
          .filter(chatId -> chatId != null)
          .forEach(targetChatIds::add);

      /* sendMessage는 실패해도 예외를 던지지 않고 false를 반환한다. */
      for (Long chatId : targetChatIds) {
        telegramBotHealthClient.sendMessage(botToken, chatId, message);
      }
    } catch (Exception e) {
      /* 다른 규칙 발송을 막지 않도록 로그만 남기고 예외를 던지지 않는다. */
      log.error(
          "{} 규칙 발송 실패 — ruleId: {}, eventType: {}, 원인: {}",
          LOG_PREFIX,
          rule.getId(),
          event.eventType(),
          e.getClass().getSimpleName());
    }
  }
}
