/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.swez.backend.domain.admin.entity.AdminTelegramCredential;
import com.swez.backend.domain.admin.repository.AdminTelegramCredentialRepository;
import com.swez.backend.domain.admin.service.TelegramCredentialCryptoService;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.calendar.entity.Submission;
import com.swez.backend.global.logging.LogMaskUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 채점 완료 후 결과 텔레그램 DM 을 학생에게 보낸다. 중복 발송을 막으려고 결과 DM 은 Spring 에서만 보낸다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionResultDmService {

  private final AdminTelegramCredentialRepository credentialRepository;
  private final TelegramCredentialCryptoService cryptoService;
  private final RestTemplate restTemplate;

  @Value("${app.telegram.api-base-url:https://api.telegram.org}")
  private String telegramApiBaseUrl;

  /**
   * 채점 결과 DM 을 해당 학생의 텔레그램으로 보낸다. 채점 결과는 이미 저장됐으므로 DM 전송 실패는 로그만 남기고
   * 무시한다.
   *
   * @param submission 채점 완료된 제출물 (officialScore, officialFeedback 채워진 상태)
   */
  public void sendResultDm(Submission submission) {
    User user = submission.getUser();
    if (user == null) {
      log.warn("[ResultDm] user 참조 없음 — DM 발송 건너뜀 | submissionId: {}", submission.getId());
      return;
    }
    Long chatId = user.getTelegramChatId();
    if (chatId == null) {
      log.info("[ResultDm] telegramChatId 없음 — 미연동 사용자 DM 생략 | userId: {}", user.getId());
      return;
    }

    String message = buildResultMessage(submission);

    Optional<AdminTelegramCredential> credentialOpt =
        credentialRepository.findAll().stream().findFirst();
    if (credentialOpt.isEmpty()) {
      log.warn(
          "[ResultDm] 봇 credential 미등록 — DM 발송 불가 | chatId: {}",
          LogMaskUtil.mask(String.valueOf(chatId)));
      return;
    }

    String botToken = cryptoService.decrypt(credentialOpt.get().getBotTokenEncrypted());
    String url = telegramApiBaseUrl + "/bot" + botToken + "/sendMessage";

    try {
      Map<String, Object> body = Map.of("chat_id", chatId, "text", message);
      restTemplate.postForObject(url, body, Map.class);
      log.info(
          "[ResultDm] 채점 결과 DM 전송 성공 | submissionId: {}, chatId: {}",
          submission.getId(),
          LogMaskUtil.mask(String.valueOf(chatId)));
    } catch (RestClientException e) {
      log.error(
          "[ResultDm] 채점 결과 DM 전송 실패 | submissionId: {}, error: {}",
          submission.getId(),
          e.getMessage());
    }
  }

  /** 채점 결과를 학생 친화적 메시지로 조립한다. */
  private String buildResultMessage(Submission submission) {
    Integer score = submission.getOfficialScore();
    String feedback = submission.getOfficialFeedback();

    StringBuilder sb = new StringBuilder();
    sb.append("채점이 완료되었습니다!\n");

    if (score != null) {
      sb.append("점수: ").append(score).append("점\n");
    }
    if (feedback != null && !feedback.isBlank()) {
      sb.append("\n피드백: ").append(feedback);
    }
    return sb.toString();
  }
}
