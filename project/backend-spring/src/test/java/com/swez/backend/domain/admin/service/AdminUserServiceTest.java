/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.swez.backend.domain.admin.dto.request.SuspendRequest;
import com.swez.backend.domain.admin.dto.request.TelegramChatIdAction;
import com.swez.backend.domain.admin.dto.request.TelegramChatIdRequest;
import com.swez.backend.domain.admin.dto.response.AdminTelegramResponse;
import com.swez.backend.domain.admin.dto.response.AdminUserSuspendResponse;
import com.swez.backend.domain.admin.exception.AdminErrorCode;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.enums.UserRole;
import com.swez.backend.domain.auth.enums.UserStatus;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.user.entity.TelegramAccount;
import com.swez.backend.domain.user.repository.TelegramAccountRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.page.OffsetPageResponse;

@DisplayName("AdminUserService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private TelegramAccountRepository telegramAccountRepository;
  @Mock private CourseRepository courseRepository;

  @InjectMocks private AdminUserService adminUserService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String OTHER_USER_ID = "usr_01BRZ3NDEKTSV4RRFFQ69G5FAV";

  private User makeUser(String id) {
    return User.builder()
        .id(id)
        .email(id + "@example.com")
        .nickname("테스터")
        .subscriptionStatus(SubscriptionStatus.FREE)
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .build();
  }

  private TelegramAccount makeTelegram(User user, Long chatId, boolean active) {
    return TelegramAccount.builder()
        .id("tga_01ARZ3NDEKTSV4RRFFQ69G5FAV")
        .user(user)
        .telegramChatId(chatId)
        .isActive(active)
        .build();
  }

  @Nested
  @DisplayName("getUsers()")
  class GetUsers {

    @Test
    @DisplayName("사용자 목록과 텔레그램 연동 상태를 페이지 응답으로 반환한다")
    void getUsers_returnsPagedUsers() {
      User user = makeUser(USER_ID);
      TelegramAccount telegram = makeTelegram(user, 123456789L, true);
      PageRequest pageable = PageRequest.of(0, 20);

      given(userRepository.findAll(any(Specification.class), any(PageRequest.class)))
          .willReturn(new PageImpl<>(List.of(user), pageable, 1));
      given(courseRepository.findByUserIdInAndStatus(any(), any(CourseStatus.class)))
          .willReturn(List.of());
      given(telegramAccountRepository.findByUserId(USER_ID)).willReturn(Optional.of(telegram));

      OffsetPageResponse<?> result =
          adminUserService.getUsers("test", SubscriptionStatus.FREE, UserStatus.ACTIVE, pageable);

      assertThat(result.getTotalElements()).isEqualTo(1);
      assertThat(result.getContent()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("toggleSuspend()")
  class ToggleSuspend {

    @Test
    @DisplayName("ACTIVE 사용자를 사유와 함께 SUSPENDED 로 변경한다")
    void toggleSuspend_activeUser_returnsSuspended() {
      User user = makeUser(USER_ID);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

      SuspendRequest request = new SuspendRequest("부적절한 활동");
      AdminUserSuspendResponse result = adminUserService.toggleSuspend(USER_ID, request);

      assertThat(result.status()).isEqualTo(UserStatus.SUSPENDED);
      assertThat(result.reason()).isEqualTo("부적절한 활동");
      assertThat(result.suspendedAt()).isNotNull();
      assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }
  }

  @Nested
  @DisplayName("updateTelegramChatId()")
  class UpdateTelegramChatId {

    @Test
    @DisplayName("set 요청은 텔레그램 계정을 생성하고 user mirror 컬럼을 동기화한다")
    void set_noExistingAccount_createsTelegramAccount() {
      User user = makeUser(USER_ID);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(telegramAccountRepository.findByTelegramChatId(123456789L))
          .willReturn(Optional.empty());
      given(telegramAccountRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

      AdminTelegramResponse result =
          adminUserService.updateTelegramChatId(
              USER_ID, new TelegramChatIdRequest(123456789L, TelegramChatIdAction.SET));

      ArgumentCaptor<TelegramAccount> captor = ArgumentCaptor.forClass(TelegramAccount.class);
      then(telegramAccountRepository).should().save(captor.capture());
      assertThat(result.linked()).isTrue();
      assertThat(result.telegramChatId()).isEqualTo(123456789L);
      assertThat(user.getTelegramChatId()).isEqualTo(123456789L);
      assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("unlink 요청은 텔레그램 계정을 비활성화하고 user mirror 컬럼을 비운다")
    void unlink_existingAccount_deactivatesTelegramAccount() {
      User user = makeUser(USER_ID);
      TelegramAccount telegram = makeTelegram(user, 123456789L, true);
      user.updateTelegramChatId(123456789L);

      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(telegramAccountRepository.findByUserId(USER_ID)).willReturn(Optional.of(telegram));

      AdminTelegramResponse result =
          adminUserService.updateTelegramChatId(
              USER_ID, new TelegramChatIdRequest(null, TelegramChatIdAction.UNLINK));

      assertThat(result.linked()).isFalse();
      assertThat(result.telegramChatId()).isNull();
      assertThat(telegram.getIsActive()).isFalse();
      assertThat(user.getTelegramChatId()).isNull();
    }

    @Test
    @DisplayName("다른 사용자의 chat_id 로 set 요청 시 ADMIN_002 예외를 던진다")
    void set_duplicateChatId_throwsAdmin002() {
      User user = makeUser(USER_ID);
      User otherUser = makeUser(OTHER_USER_ID);
      TelegramAccount otherTelegram = makeTelegram(otherUser, 123456789L, true);

      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(telegramAccountRepository.findByTelegramChatId(123456789L))
          .willReturn(Optional.of(otherTelegram));

      assertThatThrownBy(
              () ->
                  adminUserService.updateTelegramChatId(
                      USER_ID, new TelegramChatIdRequest(123456789L, TelegramChatIdAction.SET)))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AdminErrorCode.ADMIN_002));
    }

    @Test
    @DisplayName("set/relink 요청에서 chat_id 가 없으면 ADMIN_001 예외를 던진다")
    void set_missingChatId_throwsAdmin001() {
      User user = makeUser(USER_ID);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

      assertThatThrownBy(
              () ->
                  adminUserService.updateTelegramChatId(
                      USER_ID, new TelegramChatIdRequest(null, TelegramChatIdAction.SET)))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AdminErrorCode.ADMIN_001));
    }
  }
}
