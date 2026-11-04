/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.admin.entity.AdminTelegramCredential;
import com.swez.backend.domain.admin.repository.AdminTelegramCredentialRepository;
import com.swez.backend.domain.admin.service.TelegramCredentialCryptoService;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.calendar.entity.Assignment;
import com.swez.backend.domain.calendar.entity.Submission;
import com.swez.backend.domain.calendar.repository.AssignmentRepository;
import com.swez.backend.domain.calendar.repository.SubmissionRepository;
import com.swez.backend.domain.calendar.service.AssignmentGradingTriggerService;
import com.swez.backend.domain.user.entity.TelegramAccount;
import com.swez.backend.domain.user.repository.TelegramAccountRepository;
import com.swez.backend.global.logging.LogMaskUtil;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 텔레그램 웹훅 Update를 처리한다. /start {token} 명령이면 계정을 연동하고, 그 외 메시지는 과제 제출로
 * 처리한다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TelegramWebhookService {

  private static final String TG_LINK_PREFIX = "TG_LINK:";
  private static final String START_COMMAND_PREFIX = "/start ";

  private final UserRepository userRepository;
  private final TelegramAccountRepository telegramAccountRepository;
  private final AdminTelegramCredentialRepository credentialRepository;
  private final TelegramCredentialCryptoService cryptoService;
  private final AssignmentRepository assignmentRepository;
  private final SubmissionRepository submissionRepository;

  /** 텍스트 제출물의 FastAPI 채점을 비동기로 시작한다 */
  private final AssignmentGradingTriggerService gradingTriggerService;

  private final ObjectMapper objectMapper;
  private final RedisTemplate<String, String> redisTemplate;
  private final RestTemplate restTemplate;

  @Value("${app.telegram.api-base-url:https://api.telegram.org}")
  private String telegramApiBaseUrl;

  /**
   * 텔레그램 Update를 처리한다. /start {token} 명령이면 계정 연동 플로우를 실행한다. 연동된 사용자의 document/photo/text 메시지는 과제
   * 제출로 저장한다.
   */
  public void handleUpdate(Map<String, Object> update) {
    try {
      /* instanceof Map<?,?> 패턴으로 unchecked 캐스트 없이 message 를 꺼낸다. */
      if (!(update.get("message") instanceof Map<?, ?> rawMessage)) {
        return;
      }

      /* chat.id 는 연동 분기와 제출 분기 모두에서 쓴다. */
      if (!(rawMessage.get("chat") instanceof Map<?, ?> rawChat)) {
        return;
      }
      Long chatId = rawChat.get("id") instanceof Number n ? n.longValue() : null;
      if (chatId == null) {
        return;
      }

      String text = rawMessage.get("text") instanceof String s ? s : null;

      /* /start 명령이면 계정 연동으로 간다. */
      if (text != null && text.startsWith(START_COMMAND_PREFIX)) {
        String token = text.substring(START_COMMAND_PREFIX.length()).trim();
        String username = rawChat.get("username") instanceof String s ? s : null;
        log.info(
            "[TelegramWebhook] /start 명령 수신 | chatId: {}",
            LogMaskUtil.mask(String.valueOf(chatId)));
        verifyAndLink(token, chatId, username);
        return;
      }

      /* /start 이외 메시지는 연동된 사용자인지 먼저 확인한다. */
      Optional<TelegramAccount> accountOpt = telegramAccountRepository.findByTelegramChatId(chatId);
      if (accountOpt.isEmpty() || Boolean.FALSE.equals(accountOpt.get().getIsActive())) {
        /* 연동되지 않은 chat 에는 연동 안내 메시지를 보낸다. */
        sendTelegramMessage(chatId, "아직 계정이 연동되지 않았습니다. 앱에서 텔레그램 연동을 먼저 완료해 주세요.");
        return;
      }

      TelegramAccount account = accountOpt.get();
      User user = account.getUser();

      /* 제출 가능한 메시지 유형은 document, photo, text 다. */
      boolean hasDocument = rawMessage.containsKey("document");
      boolean hasPhoto = rawMessage.containsKey("photo");
      boolean hasText = text != null && !text.isBlank();

      if (!hasDocument && !hasPhoto && !hasText) {
        /* 스티커, 음성 등 지원하지 않는 메시지 유형은 무시한다. */
        log.debug(
            "[TelegramWebhook] 처리 불가 메시지 유형 무시 | chatId: {}",
            LogMaskUtil.mask(String.valueOf(chatId)));
        return;
      }

      /* 사용자의 active 과제(seed 과제 포함) 중 생성일이 가장 이른 1건에 제출한다. */
      Optional<Assignment> assignmentOpt =
          assignmentRepository.findFirstByUserIdAndStatusOrderByCreatedAtAsc(
              user.getId(), "active");
      if (assignmentOpt.isEmpty()) {
        sendTelegramMessage(chatId, "현재 제출할 수 있는 활성 과제가 없습니다.");
        log.info("[TelegramWebhook] 활성 과제 없음 — 제출 불가 | userId: {}", user.getId());
        return;
      }

      Assignment assignment = assignmentOpt.get();

      /* submitted_answers 에는 파일이면 file_id 를, 텍스트면 텍스트를 저장한다. */
      String submittedAnswersJson =
          buildSubmittedAnswersJson(rawMessage, hasDocument, hasPhoto, hasText, text);

      Submission submission =
          Submission.builder()
              .id(IdGenerator.submission())
              .assignment(assignment)
              .user(user)
              .status("queued")
              .submittedAnswers(submittedAnswersJson)
              .submittedAt(LocalDateTime.now())
              .build();

      submissionRepository.save(submission);

      log.info(
          "[TelegramWebhook] 제출 저장 완료 | submissionId: {}, assignmentId: {}, userId: {}",
          submission.getId(),
          assignment.getId(),
          user.getId());

      /* 제출 유형별 처리
       *    - 텍스트: FastAPI 채점 요청 → 채점 완료 후 Spring 콜백 → 결과 DM
       *    - 사진/파일: "준비 중" DM 만 보내고 submission 은 queued 로 둔다 (채점 요청 안 함)
       */
      if (hasText) {
        /* 접수 확인 DM 을 보낸 뒤 비동기 채점을 시작한다. */
        sendTelegramMessage(chatId, "제출이 접수되었어요. 곧 채점됩니다.");
        gradingTriggerService.triggerTextGrading(submission, assignment, text);
      } else {
        /* 현재는 텍스트 채점만 지원한다. */
        sendTelegramMessage(chatId, "사진/파일 채점은 준비 중입니다. 텍스트로 답안을 입력해 주세요.\n" + "(제출 기록은 저장되었습니다.)");
        log.info("[TelegramWebhook] 사진/파일 제출 — 채점 보류 | submissionId: {}", submission.getId());
      }

    } catch (Exception e) {
      /* 웹훅이라 어떤 예외도 밖으로 던지지 않는다. */
      log.error("[TelegramWebhook] Update 처리 중 예외 발생: {}", e.getMessage(), e);
    }
  }

  /**
   * 텔레그램 메시지 내용을 submitted_answers JSON 문자열로 직렬화한다. document이면 file_id를, photo이면 최대 해상도 file_id를,
   * 텍스트면 텍스트를 저장한다.
   */
  private String buildSubmittedAnswersJson(
      Map<?, ?> rawMessage, boolean hasDocument, boolean hasPhoto, boolean hasText, String text) {

    Map<String, Object> payload;

    if (hasDocument) {
      Map<?, ?> doc = (Map<?, ?>) rawMessage.get("document");
      String fileId = doc != null && doc.get("file_id") instanceof String s ? s : null;
      String mimeType = doc != null && doc.get("mime_type") instanceof String m ? m : null;
      payload =
          Map.of(
              "type", "document",
              "file_id", fileId != null ? fileId : "",
              "mime_type", mimeType != null ? mimeType : "");
    } else if (hasPhoto) {
      /* photo 배열의 마지막 요소가 최대 해상도라 그 file_id 를 쓴다. */
      Object photoObj = rawMessage.get("photo");
      String fileId = "";
      if (photoObj instanceof List<?> photos && !photos.isEmpty()) {
        Object last = photos.get(photos.size() - 1);
        if (last instanceof Map<?, ?> photoItem && photoItem.get("file_id") instanceof String s) {
          fileId = s;
        }
      }
      payload = Map.of("type", "photo", "file_id", fileId);
    } else {
      payload = Map.of("type", "text", "content", text != null ? text : "");
    }

    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      /* 직렬화에 실패하면 빈 JSON 객체를 반환하고 제출 저장은 계속한다. */
      log.warn("[TelegramWebhook] submitted_answers 직렬화 실패 — 빈 JSON으로 대체: {}", e.getMessage());
      return "{}";
    }
  }

  /**
   * 토큰을 검증하고 텔레그램 계정을 연동한다. 웹훅 핸들러라서 모든 에러를 텔레그램 메시지로 알리고 예외를
   * 던지지 않는다.
   */
  private void verifyAndLink(String token, Long chatId, String username) {
    /* Redis 의 TG_LINK:{token} → userId */
    String userId = redisTemplate.opsForValue().get(TG_LINK_PREFIX + token);
    if (userId == null) {
      sendTelegramMessage(chatId, "연동 토큰이 만료되었습니다. 앱에서 다시 발급해 주세요.");
      return;
    }

    Optional<User> userOpt = userRepository.findById(userId);
    if (userOpt.isEmpty()) {
      sendTelegramMessage(chatId, "사용자를 찾을 수 없습니다. 고객센터에 문의해 주세요.");
      return;
    }
    User user = userOpt.get();

    /* 이미 active TelegramAccount 가 있으면 중복 연동을 거부한다. */
    Optional<TelegramAccount> existingAccount = telegramAccountRepository.findByUserId(userId);
    if (existingAccount.isPresent() && Boolean.TRUE.equals(existingAccount.get().getIsActive())) {
      sendTelegramMessage(chatId, "이미 텔레그램이 연동된 계정입니다.");
      return;
    }

    /* chatId 가 다른 사용자에게 연동되어 있으면 거부한다. */
    Optional<TelegramAccount> chatIdAccount =
        telegramAccountRepository.findByTelegramChatId(chatId);
    if (chatIdAccount.isPresent() && !chatIdAccount.get().getUser().getId().equals(userId)) {
      sendTelegramMessage(chatId, "이 텔레그램 계정은 이미 다른 사용자에게 연결되어 있습니다.");
      return;
    }

    /* inactive 계정이 있으면 다시 활성화하고, 없으면 새로 만든다. */
    TelegramAccount account;
    if (existingAccount.isPresent()) {
      account = existingAccount.get();
      account.linkWithUsername(chatId, username);
    } else {
      account =
          TelegramAccount.builder()
              .id(IdGenerator.telegramAccount())
              .user(user)
              .telegramChatId(chatId)
              .telegramUsername(username)
              .build();
    }
    telegramAccountRepository.save(account);

    /* User.telegramChatId 미러 컬럼도 맞춘다. */
    user.updateTelegramChatId(chatId);

    /* 토큰을 무효화한다. Redis 키를 지우고 DB 토큰 컬럼도 비운다. */
    redisTemplate.delete(TG_LINK_PREFIX + token);
    user.clearTelegramLinkToken();
    userRepository.save(user);

    sendTelegramMessage(
        chatId, user.getNickname() + "님, 텔레그램 연동이 완료되었습니다! 🎉\n" + "이제 암기노트와 퀴즈를 텔레그램에서 받을 수 있어요.");

    log.info(
        "[TelegramWebhook] 연동 완료 | userId: {}, chatId: {}",
        userId,
        LogMaskUtil.mask(String.valueOf(chatId)));
  }

  /**
   * 텔레그램 Bot API로 메시지를 보낸다. TelegramNoteSender 와 같은 방식이지만 chat_id 를 직접 받는다. 웹훅 처리
   * 중이라 실패해도 예외를 던지지 않는다.
   */
  private void sendTelegramMessage(Long chatId, String text) {
    /* 관리자가 설정한 봇 토큰 중 첫 번째 credential 을 쓴다. */
    Optional<AdminTelegramCredential> credentialOpt =
        credentialRepository.findAll().stream().findFirst();
    if (credentialOpt.isEmpty()) {
      log.warn(
          "[TelegramWebhook] 봇 credential 미등록 — 메시지 전송 불가 | chatId: {}",
          LogMaskUtil.mask(String.valueOf(chatId)));
      return;
    }

    String botToken = cryptoService.decrypt(credentialOpt.get().getBotTokenEncrypted());
    String url = telegramApiBaseUrl + "/bot" + botToken + "/sendMessage";

    try {
      Map<String, Object> body =
          Map.of(
              "chat_id", chatId,
              "text", text);
      restTemplate.postForObject(url, body, Map.class);
      /* 운영에서 DEBUG 로그는 볼 수 없어 DM 전송 성공은 INFO 로 남긴다. */
      log.info(
          "[TelegramWebhook] 메시지 전송 성공 | chatId: {}", LogMaskUtil.mask(String.valueOf(chatId)));
    } catch (RestClientException e) {
      log.error(
          "[TelegramWebhook] 메시지 전송 실패 | chatId: {}, error: {}",
          LogMaskUtil.mask(String.valueOf(chatId)),
          e.getMessage());
    }
  }
}
