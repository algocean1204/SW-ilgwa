/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.exception.AuthErrorCode;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.subscription.dto.response.CurrentSubscriptionResponse;
import com.swez.backend.domain.subscription.service.SubscriptionService;
import com.swez.backend.domain.tutor.entity.Tutor;
import com.swez.backend.domain.tutor.repository.TutorRepository;
import com.swez.backend.domain.user.dto.request.OnboardingRequest;
import com.swez.backend.domain.user.dto.request.UpdateProfileRequest;
import com.swez.backend.domain.user.dto.request.UpdateSettingsRequest;
import com.swez.backend.domain.user.dto.response.OnboardingResponse;
import com.swez.backend.domain.user.dto.response.SettingsResponse;
import com.swez.backend.domain.user.dto.response.TelegramLinkResponse;
import com.swez.backend.domain.user.dto.response.TelegramStatusResponse;
import com.swez.backend.domain.user.dto.response.UserProfileImageResponse;
import com.swez.backend.domain.user.dto.response.UserProfileResponse;
import com.swez.backend.domain.user.dto.response.UserStatsResponse;
import com.swez.backend.domain.user.entity.TelegramAccount;
import com.swez.backend.domain.user.entity.UserSettings;
import com.swez.backend.domain.user.exception.UserErrorCode;
import com.swez.backend.domain.user.mapper.UserMapper;
import com.swez.backend.domain.user.repository.TelegramAccountRepository;
import com.swez.backend.domain.user.repository.UserSettingsRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.service.S3Service;

@DisplayName("UserService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private UserSettingsRepository userSettingsRepository;
  @Mock private TelegramAccountRepository telegramAccountRepository;
  @Mock private TutorRepository tutorRepository;
  @Mock private UserMapper userMapper;
  @Mock private S3Service s3Service;
  @Mock private RedisTemplate<String, String> redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;
  /* 구독 플랜은 이 서비스가 기준이다. UserService.getProfile()이 항상 호출한다 */
  @Mock private SubscriptionService subscriptionService;

  @InjectMocks private UserService userService;

  private static final String TEST_USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String TEST_EMAIL = "test@example.com";

  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOps);

    /* UserService.getProfile()이 항상 subscriptionService를 호출하므로
    테스트 전체에 FREE 플랜 기본 응답을 둬서 NPE가 나지 않게 한다 */
    CurrentSubscriptionResponse freeSubscription =
        CurrentSubscriptionResponse.builder().planCode("free").status("active").build();
    given(subscriptionService.getCurrentSubscription(anyString())).willReturn(freeSubscription);

    UserMapper realMapper = new UserMapper(new ObjectMapper());
    ReflectionTestUtils.setField(userService, "userMapper", realMapper);
  }

  private User makeUser() {
    return User.builder()
        .id(TEST_USER_ID)
        .email(TEST_EMAIL)
        .nickname("테스터")
        .onboardingCompleted(false)
        .subscriptionStatus(SubscriptionStatus.FREE)
        .build();
  }

  private UserSettings makeSettings(User user) {
    return UserSettings.defaultFor(user);
  }

  @Nested
  @DisplayName("getProfile()")
  class GetProfile {

    @Test
    @DisplayName("존재하는 사용자 조회 시 UserProfileResponse 를 반환한다")
    void getProfile_existingUser_returnsResponse() {
      // given
      User user = makeUser();
      UserSettings settings = makeSettings(user);
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(userSettingsRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.of(settings));
      given(telegramAccountRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.empty());

      // when
      UserProfileResponse result = userService.getProfile(TEST_USER_ID);

      // then
      assertThat(result.id()).isEqualTo(TEST_USER_ID);
      assertThat(result.email()).isEqualTo(TEST_EMAIL);
      assertThat(result.telegramLinked()).isFalse();
      assertThat(result.xp()).isZero(); // TODO 항목 0 반환 확인
    }

    @Test
    @DisplayName("user_settings 미존재 시 DEFAULT 설정으로 자동 생성한다")
    void getProfile_noSettings_createsDefaultSettings() {
      // given
      User user = makeUser();
      UserSettings defaultSettings = makeSettings(user);
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(userSettingsRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.empty());
      given(userSettingsRepository.save(any(UserSettings.class))).willReturn(defaultSettings);
      given(telegramAccountRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.empty());

      // when
      userService.getProfile(TEST_USER_ID);

      // then: DEFAULT 설정 생성 확인
      then(userSettingsRepository).should().save(any(UserSettings.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회 시 USER_001 예외를 던진다")
    void getProfile_unknownUser_throwsUser001() {
      // given
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> userService.getProfile(TEST_USER_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.USER_001));
    }
  }

  @Nested
  @DisplayName("updateProfile()")
  class UpdateProfile {

    @Test
    @DisplayName("요청에 포함된 필드만 업데이트하고 나머지는 기존 값을 유지한다")
    void updateProfile_partialUpdate_preservesOtherFields() {
      // given
      User user = makeUser();
      UserSettings settings = makeSettings(user);
      UpdateProfileRequest request =
          new UpdateProfileRequest("수학마스터", null, null, null, null, null, null, null);

      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(userRepository.save(any(User.class))).willReturn(user);
      given(userSettingsRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.of(settings));
      given(telegramAccountRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.empty());

      // when
      UserProfileResponse result = userService.updateProfile(TEST_USER_ID, request);

      // then
      assertThat(result.nickname()).isEqualTo("수학마스터");
      then(userRepository).should().save(user);
    }
  }

  @Nested
  @DisplayName("completeOnboarding()")
  class CompleteOnboarding {

    @Test
    @DisplayName("온보딩 완료 처리 후 프리셋 튜터 목록을 반환한다")
    void completeOnboarding_success_returnsRecommendedTutors() {
      // given
      User user = makeUser();
      Tutor preset =
          Tutor.builder()
              .tutorId("tut_00000000000000PRESET_OWL01")
              .name("올빼미 교수")
              .tagline("차분하고 단단한 설명 · 소크라테스식")
              .isDefault(true)
              .build();

      OnboardingRequest request =
          new OnboardingRequest(List.of("수학", "과학"), "고등학교 2학년", "수능 대비", "격려형", 60);

      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(userRepository.save(any(User.class))).willReturn(user);
      given(tutorRepository.findByIsDefaultTrue()).willReturn(List.of(preset));

      // when
      OnboardingResponse result = userService.completeOnboarding(TEST_USER_ID, request);

      // then
      assertThat(result.onboardingCompleted()).isTrue();
      assertThat(result.recommendedTutors()).hasSize(1);
      assertThat(result.recommendedTutors().get(0).name()).isEqualTo("올빼미 교수");
    }

    @Test
    @DisplayName("이미 온보딩 완료된 사용자 재호출 시 USER_004 예외를 던진다")
    void completeOnboarding_alreadyCompleted_throwsUser004() {
      // given
      User user =
          User.builder()
              .id(TEST_USER_ID)
              .email(TEST_EMAIL)
              .nickname("테스터")
              .onboardingCompleted(true)
              .subscriptionStatus(SubscriptionStatus.FREE)
              .build();
      OnboardingRequest request =
          new OnboardingRequest(List.of("수학"), "고등학교 1학년", "내신 향상", "격려형", null);

      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));

      // when & then
      assertThatThrownBy(() -> userService.completeOnboarding(TEST_USER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(UserErrorCode.USER_004));
    }
  }

  @Nested
  @DisplayName("generateTelegramLinkToken()")
  class GenerateTelegramLinkToken {

    @Test
    @DisplayName("텔레그램 연동 토큰을 발급하고 Redis 에 저장한다")
    void generateTelegramLinkToken_success_savesToRedis() {
      // given
      User user = makeUser();
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(telegramAccountRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.empty());
      given(userRepository.save(any(User.class))).willReturn(user);

      // when
      TelegramLinkResponse result = userService.generateTelegramLinkToken(TEST_USER_ID);

      // then
      assertThat(result.linkToken()).isNotBlank();
      assertThat(result.telegramBotUrl()).contains(result.linkToken());
      assertThat(result.expiresAt()).isAfter(java.time.OffsetDateTime.now());
      then(valueOps).should().set(anyString(), eq(TEST_USER_ID), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("이미 활성 텔레그램 계정이 있으면 USER_005 예외를 던진다")
    void generateTelegramLinkToken_activeAccount_throwsUser005() {
      // given
      User user = makeUser();
      TelegramAccount activeTg =
          TelegramAccount.builder()
              .id("tga_ACTIVE01")
              .user(user)
              .telegramChatId(123456789L)
              .isActive(true)
              .build();

      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(telegramAccountRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.of(activeTg));

      // when & then
      assertThatThrownBy(() -> userService.generateTelegramLinkToken(TEST_USER_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(UserErrorCode.USER_005));
      then(valueOps).should(never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("inactive 텔레그램 계정이 있으면 토큰 발급을 허용한다")
    void generateTelegramLinkToken_inactiveAccount_allowsToken() {
      // given
      User user = makeUser();
      TelegramAccount inactiveTg =
          TelegramAccount.builder()
              .id("tga_INACTIVE01")
              .user(user)
              .telegramChatId(123456789L)
              .isActive(false)
              .build();

      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(telegramAccountRepository.findByUserId(TEST_USER_ID))
          .willReturn(Optional.of(inactiveTg));
      given(userRepository.save(any(User.class))).willReturn(user);

      // when
      TelegramLinkResponse result = userService.generateTelegramLinkToken(TEST_USER_ID);

      // then: 토큰 발급 성공
      assertThat(result.linkToken()).isNotBlank();
    }
  }

  @Nested
  @DisplayName("getTelegramStatus()")
  class GetTelegramStatus {

    @Test
    @DisplayName("텔레그램 미연동 시 linked=false 를 반환한다")
    void getTelegramStatus_notLinked_returnsFalse() {
      // given
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(makeUser()));
      given(telegramAccountRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.empty());

      // when
      TelegramStatusResponse result = userService.getTelegramStatus(TEST_USER_ID);

      // then
      assertThat(result.linked()).isFalse();
      assertThat(result.telegramChatId()).isNull();
    }

    @Test
    @DisplayName("활성 텔레그램 계정 있을 때 linked=true 와 chatId 를 반환한다")
    void getTelegramStatus_linked_returnsChatIdAndUsername() {
      // given
      User user = makeUser();
      TelegramAccount activeTg =
          TelegramAccount.builder()
              .id("tga_ACTIVE01")
              .user(user)
              .telegramChatId(123456789L)
              .telegramUsername("testuser")
              .isActive(true)
              .build();

      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(telegramAccountRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.of(activeTg));

      // when
      TelegramStatusResponse result = userService.getTelegramStatus(TEST_USER_ID);

      // then
      assertThat(result.linked()).isTrue();
      assertThat(result.telegramChatId()).isEqualTo(123456789L);
      assertThat(result.telegramUsername()).isEqualTo("testuser");
    }
  }

  @Nested
  @DisplayName("updateSettings()")
  class UpdateSettings {

    @Test
    @DisplayName("알림 설정 Partial merge — marketing만 변경해도 나머지 키가 보존된다")
    void updateSettings_partialMerge_preservesOtherKeys() {
      // given
      User user = makeUser();
      UserSettings settings = makeSettings(user);
      UpdateSettingsRequest request =
          new UpdateSettingsRequest(
              null, null, null, new UpdateSettingsRequest.NotificationsDto(null, null, null, true));

      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(userRepository.save(any(User.class))).willReturn(user);
      given(userSettingsRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.of(settings));
      given(telegramAccountRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.empty());

      // when
      SettingsResponse result = userService.updateSettings(TEST_USER_ID, request);

      // then: lesson/assignment/community 기본값 유지, marketing만 true로 변경
      assertThat(result.notifications().lesson()).isTrue();
      assertThat(result.notifications().assignment()).isTrue();
      assertThat(result.notifications().community()).isTrue();
      assertThat(result.notifications().marketing()).isTrue();
    }
  }

  @Nested
  @DisplayName("getMyStats()")
  class GetMyStats {

    @Test
    @DisplayName("학습 통계를 정상적으로 반환한다")
    void getMyStats_returnsStats() {
      // given
      User user = makeUser();
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));

      EntityManager em = mock(EntityManager.class);
      ReflectionTestUtils.setField(userService, "entityManager", em);

      /* totalStudyMinutes 쿼리 목 */
      Query totalQuery = mock(Query.class);
      given(em.createNativeQuery(anyString())).willReturn(totalQuery);
      given(totalQuery.setParameter(eq("userId"), eq(TEST_USER_ID))).willReturn(totalQuery);
      given(totalQuery.getSingleResult()).willReturn(120);

      /* completedLessons JPQL 쿼리 목 */
      Query completedQuery = mock(Query.class);
      given(em.createQuery(anyString())).willReturn(completedQuery);
      given(completedQuery.setParameter(eq("userId"), eq(TEST_USER_ID))).willReturn(completedQuery);
      given(completedQuery.setParameter(eq("status"), any())).willReturn(completedQuery);
      given(completedQuery.getSingleResult()).willReturn(5L);

      /* streakDays NativeQuery 목: 3일 연속 학습 */
      Query streakQuery = mock(Query.class);
      given(em.createNativeQuery(anyString())).willReturn(streakQuery);
      given(streakQuery.setParameter(eq("userId"), eq(TEST_USER_ID))).willReturn(streakQuery);

      LocalDate today = LocalDate.now();
      List<Date> studyDates =
          List.of(
              Date.valueOf(today),
              Date.valueOf(today.minusDays(1)),
              Date.valueOf(today.minusDays(2)));

      /* 두 번째 createNativeQuery 호출에서 streakDays 날짜 반환 */
      given(streakQuery.getSingleResult()).willReturn(120);
      given(streakQuery.getResultList()).willReturn(studyDates);

      // when
      UserStatsResponse result = userService.getMyStats(TEST_USER_ID);

      // then
      assertThat(result.totalStudyMinutes()).isEqualTo(120);
      assertThat(result.completedLessons()).isEqualTo(5);
      assertThat(result.streakDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("학습 기록이 없을 때 모든 값이 0이다")
    void getMyStats_noData_returnsZeros() {
      // given
      User user = makeUser();
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));

      EntityManager em = mock(EntityManager.class);
      ReflectionTestUtils.setField(userService, "entityManager", em);

      Query nativeQuery = mock(Query.class);
      given(em.createNativeQuery(anyString())).willReturn(nativeQuery);
      given(nativeQuery.setParameter(eq("userId"), eq(TEST_USER_ID))).willReturn(nativeQuery);
      given(nativeQuery.getSingleResult()).willReturn(0);
      given(nativeQuery.getResultList()).willReturn(List.of());

      Query jpqlQuery = mock(Query.class);
      given(em.createQuery(anyString())).willReturn(jpqlQuery);
      given(jpqlQuery.setParameter(eq("userId"), eq(TEST_USER_ID))).willReturn(jpqlQuery);
      given(jpqlQuery.setParameter(eq("status"), any())).willReturn(jpqlQuery);
      given(jpqlQuery.getSingleResult()).willReturn(0L);

      // when
      UserStatsResponse result = userService.getMyStats(TEST_USER_ID);

      // then
      assertThat(result.totalStudyMinutes()).isZero();
      assertThat(result.completedLessons()).isZero();
      assertThat(result.streakDays()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회 시 USER_001 예외를 던진다")
    void getMyStats_unknownUser_throwsUser001() {
      // given
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> userService.getMyStats(TEST_USER_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.USER_001));
    }
  }

  @Nested
  @DisplayName("uploadProfileImage()")
  class UploadProfileImage {

    @Test
    @DisplayName("유효한 이미지 파일 업로드 시 S3에 저장하고 URL을 반환한다")
    void uploadProfileImage_validFile_returnsUrl() {
      // given
      User user = makeUser();
      MultipartFile file = mock(MultipartFile.class);
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(file.isEmpty()).willReturn(false);
      given(file.getContentType()).willReturn("image/png");
      given(file.getSize()).willReturn(1024L);
      given(file.getOriginalFilename()).willReturn("test.png");
      given(s3Service.upload(eq("user-profiles"), anyString(), eq(file), eq("image/png")))
          .willReturn("https://s3.example.com/user-profiles/uuid.png");

      // when
      UserProfileImageResponse result = userService.uploadProfileImage(TEST_USER_ID, file);

      // then
      assertThat(result.profileImageUrl())
          .isEqualTo("https://s3.example.com/user-profiles/uuid.png");
      then(s3Service).should().upload(eq("user-profiles"), anyString(), eq(file), eq("image/png"));
    }

    @Test
    @DisplayName("기존 프로필 이미지가 있으면 업로드 후 기존 이미지를 삭제한다")
    void uploadProfileImage_existingImage_deletesOldImage() {
      // given
      User user = makeUser();
      user.updateProfileImageUrl("https://s3.example.com/old-image.png");
      MultipartFile file = mock(MultipartFile.class);
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(file.isEmpty()).willReturn(false);
      given(file.getContentType()).willReturn("image/jpeg");
      given(file.getSize()).willReturn(2048L);
      given(file.getOriginalFilename()).willReturn("photo.jpg");
      given(s3Service.upload(eq("user-profiles"), anyString(), eq(file), eq("image/jpeg")))
          .willReturn("https://s3.example.com/user-profiles/new-uuid.jpg");

      // when
      userService.uploadProfileImage(TEST_USER_ID, file);

      // then
      then(s3Service).should().delete("https://s3.example.com/old-image.png");
    }

    @Test
    @DisplayName("빈 파일 업로드 시 USER_009 예외를 던진다")
    void uploadProfileImage_emptyFile_throwsUser009() {
      // given
      User user = makeUser();
      MultipartFile file = mock(MultipartFile.class);
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(file.isEmpty()).willReturn(true);

      // when & then
      assertThatThrownBy(() -> userService.uploadProfileImage(TEST_USER_ID, file))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(UserErrorCode.USER_009));
    }

    @Test
    @DisplayName("지원하지 않는 이미지 형식 업로드 시 USER_010 예외를 던진다")
    void uploadProfileImage_unsupportedType_throwsUser010() {
      // given
      User user = makeUser();
      MultipartFile file = mock(MultipartFile.class);
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(file.isEmpty()).willReturn(false);
      given(file.getContentType()).willReturn("image/gif");

      // when & then
      assertThatThrownBy(() -> userService.uploadProfileImage(TEST_USER_ID, file))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(UserErrorCode.USER_010));
    }

    @Test
    @DisplayName("5MB 초과 파일 업로드 시 USER_011 예외를 던진다")
    void uploadProfileImage_sizeExceeded_throwsUser011() {
      // given
      User user = makeUser();
      MultipartFile file = mock(MultipartFile.class);
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(file.isEmpty()).willReturn(false);
      given(file.getContentType()).willReturn("image/png");
      given(file.getSize()).willReturn(6 * 1024 * 1024L);

      // when & then
      assertThatThrownBy(() -> userService.uploadProfileImage(TEST_USER_ID, file))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(UserErrorCode.USER_011));
    }

    @Test
    @DisplayName("S3 업로드 실패 시 USER_012 예외를 던진다")
    void uploadProfileImage_s3Failure_throwsUser012() {
      // given
      User user = makeUser();
      MultipartFile file = mock(MultipartFile.class);
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(file.isEmpty()).willReturn(false);
      given(file.getContentType()).willReturn("image/png");
      given(file.getSize()).willReturn(1024L);
      given(file.getOriginalFilename()).willReturn("test.png");
      given(s3Service.upload(anyString(), anyString(), any(MultipartFile.class), anyString()))
          .willThrow(new RuntimeException("S3 connection failed"));

      // when & then
      assertThatThrownBy(() -> userService.uploadProfileImage(TEST_USER_ID, file))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(UserErrorCode.USER_012));
    }
  }

  @Nested
  @DisplayName("deleteProfileImage()")
  class DeleteProfileImage {

    @Test
    @DisplayName("기존 프로필 이미지가 있으면 URL을 제거하고 S3에서 삭제한다")
    void deleteProfileImage_existingImage_deletesFromS3() {
      // given
      User user = makeUser();
      user.updateProfileImageUrl("https://s3.example.com/user-profiles/old.png");
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));

      // when
      userService.deleteProfileImage(TEST_USER_ID);

      // then
      assertThat(user.getProfileImageUrl()).isNull();
      then(s3Service).should().delete("https://s3.example.com/user-profiles/old.png");
    }

    @Test
    @DisplayName("프로필 이미지가 없으면 S3 삭제를 호출하지 않는다")
    void deleteProfileImage_noImage_skipsS3Delete() {
      // given
      User user = makeUser();
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));

      // when
      userService.deleteProfileImage(TEST_USER_ID);

      // then
      assertThat(user.getProfileImageUrl()).isNull();
      then(s3Service).should(never()).delete(anyString());
    }
  }
}
