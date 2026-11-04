/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swez.backend.domain.admin.dto.request.TelegramCredentialUpdateRequest;
import com.swez.backend.domain.admin.dto.response.TelegramCredentialResponse;
import com.swez.backend.domain.admin.dto.response.TelegramCredentialUpdateResponse;
import com.swez.backend.domain.admin.dto.response.TelegramMessageListResponse;
import com.swez.backend.domain.admin.dto.response.TelegramSessionListResponse;
import com.swez.backend.domain.admin.entity.AdminTelegramCredential;
import com.swez.backend.domain.admin.entity.SocraticSession;
import com.swez.backend.domain.admin.entity.TelegramMessage;
import com.swez.backend.domain.admin.exception.AdminErrorCode;
import com.swez.backend.domain.admin.repository.AdminTelegramCredentialRepository;
import com.swez.backend.domain.admin.repository.SocraticSessionRepository;
import com.swez.backend.domain.admin.repository.TelegramMessageRepository;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.calendar.entity.Assignment;
import com.swez.backend.domain.calendar.entity.Submission;
import com.swez.backend.domain.calendar.repository.SubmissionRepository;
import com.swez.backend.domain.user.entity.TelegramAccount;
import com.swez.backend.domain.user.repository.TelegramAccountRepository;
import com.swez.backend.global.exception.CustomException;

@DisplayName("AdminTelegramService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class AdminTelegramServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private TelegramAccountRepository telegramAccountRepository;
  @Mock private AdminTelegramCredentialRepository credentialRepository;
  @Mock private TelegramMessageRepository telegramMessageRepository;
  @Mock private SocraticSessionRepository socraticSessionRepository;
  @Mock private SubmissionRepository submissionRepository;
  @Mock private TelegramBotHealthClient healthClient;

  @Spy
  private TelegramCredentialCryptoService cryptoService =
      new TelegramCredentialCryptoService("test-secret-key");

  @InjectMocks private AdminTelegramService adminTelegramService;

  private static final String ADMIN_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String USER_ID = "usr_01BRZ3NDEKTSV4RRFFQ69G5FAV";

  private User makeUser(String id) {
    return User.builder()
        .id(id)
        .email(id + "@example.com")
        .nickname("테스터")
        .subscriptionStatus(SubscriptionStatus.FREE)
        .build();
  }

  @Nested
  @DisplayName("getCredential()")
  class GetCredential {

    @Test
    @DisplayName("credential이 없으면 null 필드 응답을 반환한다")
    void getCredential_notFound_returnsEmptyResponse() {
      given(credentialRepository.findByUserId(ADMIN_ID)).willReturn(Optional.empty());

      TelegramCredentialResponse result = adminTelegramService.getCredential(ADMIN_ID);

      assertThat(result.botTokenFingerprint()).isNull();
      assertThat(result.adminChatId()).isNull();
    }
  }

  @Nested
  @DisplayName("updateCredential()")
  class UpdateCredential {

    @Test
    @DisplayName("새 botToken은 암호문과 fingerprint로 저장하고 평문을 저장하지 않는다")
    void updateCredential_newToken_encryptsAndFingerprints() {
      User admin = makeUser(ADMIN_ID);
      given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
      given(credentialRepository.findByUserId(ADMIN_ID)).willReturn(Optional.empty());
      given(healthClient.getMe("123456:token")).willReturn(true);
      given(credentialRepository.save(any(AdminTelegramCredential.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      TelegramCredentialUpdateResponse result =
          adminTelegramService.updateCredential(
              ADMIN_ID, new TelegramCredentialUpdateRequest("123456:token", 987654321L));

      ArgumentCaptor<AdminTelegramCredential> captor =
          ArgumentCaptor.forClass(AdminTelegramCredential.class);
      then(credentialRepository).should().save(captor.capture());
      AdminTelegramCredential saved = captor.getValue();
      assertThat(saved.getBotTokenEncrypted()).isNotEqualTo("123456:token");
      assertThat(saved.getBotTokenFingerprint()).endsWith("...");
      assertThat(saved.getAdminChatId()).isEqualTo(987654321L);
      assertThat(saved.getLastHealthCheckAt()).isNotNull();
      assertThat(result.healthCheckPassed()).isTrue();
    }

    @Test
    @DisplayName("수정 값이 없으면 ADMIN_003 예외를 던진다")
    void updateCredential_emptyRequest_throwsAdmin003() {
      assertThatThrownBy(
              () ->
                  adminTelegramService.updateCredential(
                      ADMIN_ID, new TelegramCredentialUpdateRequest(null, null)))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AdminErrorCode.ADMIN_003));
    }
  }

  @Nested
  @DisplayName("getMessages()")
  class GetMessages {

    @Test
    @DisplayName("chat_id에 연결된 사용자의 텔레그램 메시지를 조회한다")
    void getMessages_existingChatId_returnsMessages() {
      User user = makeUser(USER_ID);
      TelegramAccount account =
          TelegramAccount.builder()
              .id("tga_01ARZ3NDEKTSV4RRFFQ69G5FAV")
              .user(user)
              .telegramChatId(123456789L)
              .build();
      TelegramMessage message =
          TelegramMessage.builder()
              .id("tgm_01ARZ3NDEKTSV4RRFFQ69G5FAV")
              .user(user)
              .direction("in")
              .messageType("assignment")
              .content("제출합니다")
              .createdAt(LocalDateTime.now())
              .build();
      LocalDateTime from = LocalDateTime.now().minusDays(1);
      LocalDateTime to = LocalDateTime.now();

      given(telegramAccountRepository.findByTelegramChatId(123456789L))
          .willReturn(Optional.of(account));
      given(
              telegramMessageRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                  USER_ID, from, to))
          .willReturn(List.of(message));

      TelegramMessageListResponse result = adminTelegramService.getMessages(123456789L, from, to);

      assertThat(result.messages()).hasSize(1);
      assertThat(result.messages().get(0).text()).isEqualTo("제출합니다");
    }
  }

  @Nested
  @DisplayName("getSessions()")
  class GetSessions {

    @Test
    @DisplayName("소크라테스 세션과 채점 제출 세션을 통합 조회한다")
    void getSessions_mergesSocraticAndSubmissionSessions() {
      User user = makeUser(USER_ID);
      Assignment assignment =
          Assignment.builder().id("asg_01ARZ3NDEKTSV4RRFFQ69G5FAV").user(user).build();
      Submission submission =
          Submission.builder()
              .id("sbm_01ARZ3NDEKTSV4RRFFQ69G5FAV")
              .user(user)
              .assignment(assignment)
              .status("done")
              .createdAt(LocalDateTime.now().minusHours(1))
              .build();
      SocraticSession socraticSession =
          SocraticSession.builder()
              .id("soc_01ARZ3NDEKTSV4RRFFQ69G5FAV")
              .user(user)
              .discussionTopic("미분")
              .createdAt(LocalDateTime.now())
              .build();

      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(socraticSessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER_ID))
          .willReturn(List.of(socraticSession));
      given(submissionRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER_ID))
          .willReturn(List.of(submission));

      TelegramSessionListResponse result = adminTelegramService.getSessions(USER_ID);

      assertThat(result.sessions()).hasSize(2);
      assertThat(result.sessions().get(0).sessionType()).isEqualTo("socratic");
      assertThat(result.sessions().get(1).sessionType()).isEqualTo("grading");
    }
  }
}
