/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.admin.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.swez.backend.domain.admin.entity.AdminAlertRule;
import com.swez.backend.domain.admin.entity.AdminTelegramCredential;
import com.swez.backend.domain.admin.entity.AdminTelegramRecipient;
import com.swez.backend.domain.admin.enums.AlertEventType;
import com.swez.backend.domain.admin.enums.AlertSeverity;
import com.swez.backend.domain.admin.event.AlertEvent;
import com.swez.backend.domain.admin.repository.AdminAlertRuleRepository;
import com.swez.backend.domain.admin.repository.AdminTelegramRecipientRepository;

@DisplayName("AlertDispatchService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertDispatchServiceTest {

  @Mock private AdminAlertRuleRepository alertRuleRepository;
  @Mock private AdminTelegramRecipientRepository recipientRepository;
  @Mock private TelegramCredentialCryptoService cryptoService;
  @Mock private TelegramBotHealthClient telegramBotHealthClient;

  @InjectMocks private AlertDispatchService alertDispatchService;

  private static final String BOT_TOKEN = "bot-token";

  /** adminChatId·암호문을 가진 CRITICAL 규칙 하나를 만든다. */
  private AdminAlertRule ruleWithAdminChatId(Long adminChatId) {
    AdminTelegramCredential credential =
        AdminTelegramCredential.builder()
            .adminChatId(adminChatId)
            .botTokenEncrypted("enc")
            .build();
    return AdminAlertRule.builder()
        .credential(credential)
        .severity(AlertSeverity.CRITICAL)
        .build();
  }

  private AdminTelegramRecipient recipient(Long chatId) {
    return AdminTelegramRecipient.builder().chatId(chatId).build();
  }

  @Test
  @DisplayName("관리자 chat_id가 수신자로도 등록돼 있으면 동일 채팅방에 1회만 발송한다")
  void adminIsAlsoRecipient_sendsOnce() {
    // given: adminChatId == recipient.chatId (운영에서 관측된 중복 발송 케이스)
    given(alertRuleRepository.findByEventTypeAndEnabledTrue(any()))
        .willReturn(List.of(ruleWithAdminChatId(8209833102L)));
    given(cryptoService.decrypt("enc")).willReturn(BOT_TOKEN);
    given(recipientRepository.findByCredentialId(any()))
        .willReturn(List.of(recipient(8209833102L)));

    // when
    alertDispatchService.onAlertEvent(new AlertEvent(AlertEventType.PAYMENT_FAILED, "detail"));

    // then: 같은 chat_id로는 1번만 보낸다 (admin과 recipient 경로 중복 제거)
    then(telegramBotHealthClient)
        .should(times(1))
        .sendMessage(eq(BOT_TOKEN), eq(8209833102L), anyString());
    then(telegramBotHealthClient).should(times(1)).sendMessage(any(), any(), anyString());
  }

  @Test
  @DisplayName("관리자와 수신자 chat_id가 다르면 각각 1회씩 발송한다")
  void adminAndDistinctRecipient_sendsToEach() {
    // given
    given(alertRuleRepository.findByEventTypeAndEnabledTrue(any()))
        .willReturn(List.of(ruleWithAdminChatId(100L)));
    given(cryptoService.decrypt("enc")).willReturn(BOT_TOKEN);
    given(recipientRepository.findByCredentialId(any())).willReturn(List.of(recipient(200L)));

    // when
    alertDispatchService.onAlertEvent(new AlertEvent(AlertEventType.PAYMENT_FAILED, "detail"));

    // then: 서로 다른 두 채팅방에 각 1번 (총 2번)
    then(telegramBotHealthClient).should(times(1)).sendMessage(eq(BOT_TOKEN), eq(100L), anyString());
    then(telegramBotHealthClient).should(times(1)).sendMessage(eq(BOT_TOKEN), eq(200L), anyString());
    then(telegramBotHealthClient).should(times(2)).sendMessage(any(), any(), anyString());
  }
}
