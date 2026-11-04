/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.swez.backend.domain.admin.dto.request.AddRecipientRequest;
import com.swez.backend.domain.admin.dto.request.TelegramCredentialUpdateRequest;
import com.swez.backend.domain.admin.dto.response.RecipientListResponse;
import com.swez.backend.domain.admin.dto.response.RecipientResponse;
import com.swez.backend.domain.admin.dto.response.TelegramCredentialResponse;
import com.swez.backend.domain.admin.dto.response.TelegramCredentialUpdateResponse;
import com.swez.backend.domain.admin.dto.response.TelegramMessageListResponse;
import com.swez.backend.domain.admin.dto.response.TelegramSessionListResponse;
import com.swez.backend.domain.admin.entity.AdminTelegramCredential;
import com.swez.backend.domain.admin.entity.AdminTelegramRecipient;
import com.swez.backend.domain.admin.entity.SocraticSession;
import com.swez.backend.domain.admin.entity.TelegramMessage;
import com.swez.backend.domain.admin.exception.AdminErrorCode;
import com.swez.backend.domain.admin.repository.AdminTelegramCredentialRepository;
import com.swez.backend.domain.admin.repository.AdminTelegramRecipientRepository;
import com.swez.backend.domain.admin.repository.SocraticSessionRepository;
import com.swez.backend.domain.admin.repository.TelegramMessageRepository;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.exception.AuthErrorCode;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.calendar.entity.Submission;
import com.swez.backend.domain.calendar.repository.SubmissionRepository;
import com.swez.backend.domain.user.entity.TelegramAccount;
import com.swez.backend.domain.user.repository.TelegramAccountRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTelegramService {

  private final UserRepository userRepository;
  private final TelegramAccountRepository telegramAccountRepository;
  private final AdminTelegramCredentialRepository credentialRepository;
  private final AdminTelegramRecipientRepository recipientRepository;
  private final TelegramMessageRepository telegramMessageRepository;
  private final SocraticSessionRepository socraticSessionRepository;
  private final SubmissionRepository submissionRepository;
  private final TelegramCredentialCryptoService cryptoService;
  private final TelegramBotHealthClient healthClient;

  public TelegramCredentialResponse getCredential(String adminUserId) {
    return credentialRepository
        .findByUserId(adminUserId)
        .map(this::toCredentialResponse)
        .orElse(new TelegramCredentialResponse(null, null, null, null));
  }

  @Transactional
  public TelegramCredentialUpdateResponse updateCredential(
      String adminUserId, TelegramCredentialUpdateRequest request) {
    if (!StringUtils.hasText(request.botToken()) && request.adminChatId() == null) {
      throw new CustomException(AdminErrorCode.ADMIN_003);
    }

    User adminUser = getUser(adminUserId);
    Optional<AdminTelegramCredential> existing = credentialRepository.findByUserId(adminUserId);
    if (existing.isEmpty() && !StringUtils.hasText(request.botToken())) {
      throw new CustomException(AdminErrorCode.ADMIN_003);
    }

    AdminTelegramCredential credential =
        existing.orElseGet(
            () ->
                AdminTelegramCredential.builder()
                    .user(adminUser)
                    .botTokenEncrypted(cryptoService.encrypt(request.botToken()))
                    .botTokenFingerprint(cryptoService.fingerprint(request.botToken()))
                    .lastRotatedAt(LocalDateTime.now())
                    .build());

    String healthCheckToken;
    if (StringUtils.hasText(request.botToken())) {
      credential.rotateBotToken(
          cryptoService.encrypt(request.botToken()), cryptoService.fingerprint(request.botToken()));
      healthCheckToken = request.botToken();
    } else {
      healthCheckToken = cryptoService.decrypt(credential.getBotTokenEncrypted());
    }
    if (request.adminChatId() != null) {
      credential.updateAdminChatId(request.adminChatId());
    }

    boolean healthCheckPassed = healthClient.getMe(healthCheckToken);
    if (healthCheckPassed) {
      credential.markHealthCheckSuccess();
    }
    AdminTelegramCredential saved = credentialRepository.save(credential);
    return new TelegramCredentialUpdateResponse(
        saved.getBotTokenFingerprint(), saved.getLastRotatedAt(), healthCheckPassed);
  }

  public TelegramMessageListResponse getMessages(
      Long chatId, LocalDateTime from, LocalDateTime to) {
    TelegramAccount account =
        telegramAccountRepository
            .findByTelegramChatId(chatId)
            .orElseThrow(() -> new CustomException(AdminErrorCode.ADMIN_004));
    List<TelegramMessageListResponse.MessageItem> messages =
        telegramMessageRepository
            .findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                account.getUser().getId(), from, to)
            .stream()
            .map(this::toMessageItem)
            .toList();
    return new TelegramMessageListResponse(messages);
  }

  public TelegramSessionListResponse getSessions(String userId) {
    getUser(userId);
    List<TelegramSessionListResponse.SessionItem> socraticSessions =
        socraticSessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toSessionItem)
            .toList();
    List<TelegramSessionListResponse.SessionItem> gradingSessions =
        submissionRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toSessionItem)
            .toList();
    List<TelegramSessionListResponse.SessionItem> sessions =
        java.util.stream.Stream.concat(socraticSessions.stream(), gradingSessions.stream())
            .sorted(
                Comparator.comparing(TelegramSessionListResponse.SessionItem::startedAt).reversed())
            .limit(40)
            .toList();
    return new TelegramSessionListResponse(sessions);
  }

  /** 등록된 알림 수신자 목록을 조회한다 */
  public RecipientListResponse getRecipients(String adminUserId) {
    AdminTelegramCredential credential = getCredentialByUserId(adminUserId);
    List<RecipientResponse> recipients =
        recipientRepository.findByCredentialId(credential.getId()).stream()
            .map(this::toRecipientResponse)
            .toList();
    return new RecipientListResponse(recipients);
  }

  /** 새 알림 수신자를 추가한다. 같은 credential 안에서 chatId가 겹치면 ADMIN_007. */
  @Transactional
  public RecipientResponse addRecipient(String adminUserId, AddRecipientRequest request) {
    AdminTelegramCredential credential = getCredentialByUserId(adminUserId);
    recipientRepository
        .findByCredentialIdAndChatId(credential.getId(), request.chatId())
        .ifPresent(
            existing -> {
              throw new CustomException(AdminErrorCode.ADMIN_007);
            });
    AdminTelegramRecipient recipient =
        AdminTelegramRecipient.builder()
            .id(IdGenerator.adminTelegramRecipient())
            .credential(credential)
            .nickname(request.nickname())
            .chatId(request.chatId())
            .build();
    AdminTelegramRecipient saved = recipientRepository.save(recipient);
    return toRecipientResponse(saved);
  }

  /** 소유권을 확인한 뒤 수신자를 삭제한다. 소유자가 다르면 ADMIN_008. */
  @Transactional
  public void removeRecipient(String adminUserId, String recipientId) {
    AdminTelegramCredential credential = getCredentialByUserId(adminUserId);
    AdminTelegramRecipient recipient =
        recipientRepository
            .findById(recipientId)
            .orElseThrow(() -> new CustomException(AdminErrorCode.ADMIN_008));
    if (!recipient.getCredential().getId().equals(credential.getId())) {
      throw new CustomException(AdminErrorCode.ADMIN_008);
    }
    recipientRepository.delete(recipient);
  }

  private RecipientResponse toRecipientResponse(AdminTelegramRecipient recipient) {
    return new RecipientResponse(
        recipient.getId(),
        recipient.getNickname(),
        recipient.getChatId(),
        recipient.getCreatedAt());
  }

  /** 관리자 사용자의 credential을 조회한다. 없으면 ADMIN_006. */
  private AdminTelegramCredential getCredentialByUserId(String adminUserId) {
    return credentialRepository
        .findByUserId(adminUserId)
        .orElseThrow(() -> new CustomException(AdminErrorCode.ADMIN_006));
  }

  private TelegramCredentialResponse toCredentialResponse(AdminTelegramCredential credential) {
    return new TelegramCredentialResponse(
        credential.getBotTokenFingerprint(),
        credential.getAdminChatId(),
        credential.getLastRotatedAt(),
        credential.getLastHealthCheckAt());
  }

  private TelegramMessageListResponse.MessageItem toMessageItem(TelegramMessage message) {
    return new TelegramMessageListResponse.MessageItem(
        message.getId(),
        message.getDirection(),
        message.getMessageType(),
        message.getContent(),
        message.getImageUrl(),
        message.getReferenceType(),
        message.getReferenceId(),
        message.getTelegramMsgId(),
        message.getCreatedAt());
  }

  private TelegramSessionListResponse.SessionItem toSessionItem(SocraticSession session) {
    return new TelegramSessionListResponse.SessionItem(
        session.getId(), "socratic", session.getCreatedAt(), null, session.getDiscussionTopic());
  }

  private TelegramSessionListResponse.SessionItem toSessionItem(Submission submission) {
    return new TelegramSessionListResponse.SessionItem(
        submission.getId(),
        "grading",
        submission.getCreatedAt(),
        submission.getStatus(),
        submission.getAssignment().getId());
  }

  private User getUser(String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(AuthErrorCode.USER_001));
  }
}
