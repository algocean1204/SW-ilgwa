/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.exception.AuthErrorCode;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.course.enums.ChapterStatus;
import com.swez.backend.domain.subscription.dto.response.CurrentSubscriptionResponse;
import com.swez.backend.domain.subscription.service.SubscriptionService;
import com.swez.backend.domain.tutor.entity.Tutor;
import com.swez.backend.domain.tutor.repository.TutorRepository;
import com.swez.backend.domain.user.dto.request.OnboardingRequest;
import com.swez.backend.domain.user.dto.request.UpdateProfileRequest;
import com.swez.backend.domain.user.dto.request.UpdateSettingsRequest;
import com.swez.backend.domain.user.dto.request.UpdateTelegramPreferencesRequest;
import com.swez.backend.domain.user.dto.response.OnboardingResponse;
import com.swez.backend.domain.user.dto.response.SettingsResponse;
import com.swez.backend.domain.user.dto.response.TelegramLinkResponse;
import com.swez.backend.domain.user.dto.response.TelegramPreferencesResponse;
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
import com.swez.backend.global.logging.LogMaskUtil;
import com.swez.backend.global.service.S3Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 사용자 프로필·온보딩·텔레그램 연동·설정을 처리한다. */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

  private static final String TG_LINK_PREFIX = "TG_LINK:";
  private static final long TG_LINK_TTL_MINUTES = 5L;
  private static final String TELEGRAM_BOT_URL = "https://t.me/ezstudy_bot?start=";

  private static final java.util.Set<String> ALLOWED_IMAGE_TYPES =
      java.util.Set.of("image/jpeg", "image/png", "image/webp");
  private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;

  private final UserRepository userRepository;
  private final UserSettingsRepository userSettingsRepository;
  private final TelegramAccountRepository telegramAccountRepository;
  private final TutorRepository tutorRepository;
  private final UserMapper userMapper;
  private final S3Service s3Service;
  private final RedisTemplate<String, String> redisTemplate;
  /*
   * 구독 플랜 정보의 기준은 subscription 테이블이다. User 엔티티의 캐시 필드 대신 이 서비스로 읽어
   * profile 과 subscription 엔드포인트의 플랜 정보가 같게 한다.
   */
  private final SubscriptionService subscriptionService;

  @PersistenceContext private EntityManager entityManager;

  /**
   * 내 프로필 조회. user_settings 가 없으면 DEFAULT 값으로 만든다.
   *
   * <p>구독 플랜은 SubscriptionService 값을 기준으로 쓴다. User 엔티티의 subscriptionStatus 캐시 필드는 쓰지 않아
   * profile 과 subscription 응답이 어긋나지 않는다.
   */
  @Transactional
  public UserProfileResponse getProfile(String userId) {
    log.debug("[UserService] getProfile() - START | userId: {}", userId);

    User user = findUserById(userId);

    /* 사용자 설정이 없으면 DEFAULT 값으로 만든다. */
    UserSettings settings =
        userSettingsRepository.findByUserId(userId).orElseGet(() -> createDefaultSettings(user));

    TelegramAccount telegram = telegramAccountRepository.findByUserId(userId).orElse(null);

    /* 구독 정보는 User 엔티티 캐시 대신 기준인 SubscriptionService 에서 조회한다. */
    CurrentSubscriptionResponse subscription = subscriptionService.getCurrentSubscription(userId);

    UserProfileResponse result =
        userMapper.toProfileResponse(user, settings, telegram, subscription);

    log.debug("[UserService] getProfile() - END | userId: {}", userId);
    return result;
  }

  /** 프로필 부분 수정. null 필드는 기존 값을 유지한다. */
  @Transactional
  public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
    log.debug("[UserService] updateProfile() - START | userId: {}", userId);

    User user = findUserById(userId);

    /* interests 가 null 이면 직렬화하지 않는다. */
    String interestsJson =
        request.interests() != null ? userMapper.serializeStringList(request.interests()) : null;

    user.updateProfile(
        request.nickname(),
        request.profileImageUrl(),
        request.grade(),
        request.major(),
        request.education(),
        request.learningGoal(),
        interestsJson,
        request.preferredTone());
    userRepository.save(user);

    UserProfileResponse result = getProfile(userId);

    log.debug("[UserService] updateProfile() - END | userId: {}", userId);
    return result;
  }

  /**
   * 온보딩 완료 처리. 이미 완료된 사용자면 USER_004 에러.
   *
   * @return 온보딩 완료 응답 (추천 선생님 포함)
   */
  @Transactional
  public OnboardingResponse completeOnboarding(String userId, OnboardingRequest request) {
    log.debug("[UserService] completeOnboarding() - START | userId: {}", userId);

    User user = findUserById(userId);

    if (Boolean.TRUE.equals(user.getOnboardingCompleted())) {
      throw new CustomException(UserErrorCode.USER_004);
    }

    String interestsJson = userMapper.serializeStringList(request.interests());
    user.updateOnboardingInfo(
        request.grade(),
        request.learningGoal(),
        interestsJson,
        request.preferredTone(),
        request.dailyStudyGoalMinutes());
    user.completeOnboarding();
    userRepository.save(user);

    List<Tutor> presets = tutorRepository.findByIsDefaultTrue();
    OnboardingResponse result = userMapper.toOnboardingResponse(presets);

    log.debug("[UserService] completeOnboarding() - END | userId: {}", userId);
    return result;
  }

  /** 텔레그램 연동 토큰 발급. is_active=true 계정이 있으면 USER_005 에러. */
  @Transactional
  public TelegramLinkResponse generateTelegramLinkToken(String userId) {
    log.debug("[UserService] generateTelegramLinkToken() - START | userId: {}", userId);

    User user = findUserById(userId);

    telegramAccountRepository
        .findByUserId(userId)
        .ifPresent(
            tg -> {
              if (Boolean.TRUE.equals(tg.getIsActive())) {
                throw new CustomException(UserErrorCode.USER_005);
              }
              /* inactive 계정이면 토큰을 발급한다. 실제 row 갱신은 봇 콜백에서 한다. */
            });

    String token = UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 16);

    /* Redis 에 TTL 5분으로 저장한다. */
    redisTemplate
        .opsForValue()
        .set(TG_LINK_PREFIX + token, userId, TG_LINK_TTL_MINUTES, TimeUnit.MINUTES);

    user.issueTelegramLinkToken(token);
    userRepository.save(user);

    /* 만료 시각은 UTC 기준 OffsetDateTime 으로 만든다.
     * LocalDateTime.now() 는 timezone 정보가 없어 "2026-06-04T08:53" 형태로 직렬화되고, 브라우저가 이를
     * KST 로컬로 해석하면 9시간 이른 시각으로 보아 바로 만료 오류를 낸다.
     * OffsetDateTime.now(ZoneOffset.UTC) 는 "2026-06-04T08:53:00Z" 로 직렬화되어 브라우저가 UTC 로 읽는다. */
    OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(TG_LINK_TTL_MINUTES);
    TelegramLinkResponse result =
        TelegramLinkResponse.builder()
            .linkToken(token)
            .telegramBotUrl(TELEGRAM_BOT_URL + token)
            .expiresAt(expiresAt)
            .build();

    log.debug("[UserService] generateTelegramLinkToken() - END | userId: {}", userId);
    return result;
  }

  /** 텔레그램 연동 상태 조회. */
  @Transactional(readOnly = true)
  public TelegramStatusResponse getTelegramStatus(String userId) {
    log.debug("[UserService] getTelegramStatus() - START | userId: {}", userId);

    findUserById(userId);

    TelegramAccount telegram = telegramAccountRepository.findByUserId(userId).orElse(null);
    TelegramStatusResponse result = userMapper.toTelegramStatusResponse(telegram);

    log.debug("[UserService] getTelegramStatus() - END | userId: {}", userId);
    return result;
  }

  /** 텔레그램 자동 전송 옵션 조회. user_settings 가 없으면 DEFAULT 값으로 만든다. */
  @Transactional
  public TelegramPreferencesResponse getTelegramPreferences(String userId) {
    log.debug("[UserService] getTelegramPreferences() - START | userId: {}", userId);

    User user = findUserById(userId);

    /* getProfile 과 같이 설정이 없으면 DEFAULT 값으로 만든다. */
    UserSettings settings =
        userSettingsRepository.findByUserId(userId).orElseGet(() -> createDefaultSettings(user));

    TelegramPreferencesResponse result = userMapper.toTelegramPreferencesResponse(settings);

    log.debug("[UserService] getTelegramPreferences() - END | userId: {}", userId);
    return result;
  }

  /** 텔레그램 자동 전송 옵션 부분 수정. null 필드는 기존 값을 유지한다. */
  @Transactional
  public TelegramPreferencesResponse updateTelegramPreferences(
      String userId, UpdateTelegramPreferencesRequest request) {
    log.debug("[UserService] updateTelegramPreferences() - START | userId: {}", userId);

    User user = findUserById(userId);

    UserSettings settings =
        userSettingsRepository.findByUserId(userId).orElseGet(() -> createDefaultSettings(user));

    /* 상태 변경은 엔티티 메서드로 하고, null 은 기존 값을 유지한다. */
    settings.updateTelegramPreferences(
        request.noteEnabled(), request.quizEnabled(), request.friendEnabled());
    userSettingsRepository.save(settings);

    TelegramPreferencesResponse result = userMapper.toTelegramPreferencesResponse(settings);

    log.debug("[UserService] updateTelegramPreferences() - END | userId: {}", userId);
    return result;
  }

  /** 접근성·알림 설정을 기존 JSON 에 부분 병합한다. */
  @Transactional
  public SettingsResponse updateSettings(String userId, UpdateSettingsRequest request) {
    log.debug("[UserService] updateSettings() - START | userId: {}", userId);

    User user = findUserById(userId);

    if (request.dailyStudyGoalMinutes() != null) {
      user.updateDailyStudyGoalMinutes(request.dailyStudyGoalMinutes());
    }

    if (request.highContrast() != null || request.keyboardNavigation() != null) {
      Map<String, Object> accessibilityUpdates = new HashMap<>();
      if (request.highContrast() != null)
        accessibilityUpdates.put("highContrast", request.highContrast());
      if (request.keyboardNavigation() != null)
        accessibilityUpdates.put("keyboardNavigation", request.keyboardNavigation());

      String mergedAccessibility =
          userMapper.mergeJson(
              user.getAccessibilitySettings(),
              accessibilityUpdates,
              userMapper.defaultAccessibilitySettings());
      user.updateAccessibilitySettings(mergedAccessibility);
    }

    if (request.notifications() != null) {
      UpdateSettingsRequest.NotificationsDto n = request.notifications();
      Map<String, Object> notificationUpdates = new HashMap<>();
      if (n.lesson() != null) notificationUpdates.put("lesson", n.lesson());
      if (n.assignment() != null) notificationUpdates.put("assignment", n.assignment());
      if (n.community() != null) notificationUpdates.put("community", n.community());
      if (n.marketing() != null) notificationUpdates.put("marketing", n.marketing());

      String mergedNotifications =
          userMapper.mergeJson(
              user.getNotificationSettings(),
              notificationUpdates,
              userMapper.defaultNotificationSettings());
      user.updateNotificationSettings(mergedNotifications);
    }

    userRepository.save(user);
    UserProfileResponse profile = getProfile(userId);
    SettingsResponse result = profile.settings();

    log.debug("[UserService] updateSettings() - END | userId: {}", userId);
    return result;
  }

  // 대시보드

  /**
   * 학습 통계 요약을 조회한다. 총 학습 시간(study_time_log SUM), 완료한 강의 수(COMPLETED 챕터 수), 연속 학습
   * 일수(study_time_log 일별 기록으로 계산)를 담는다.
   */
  @Transactional(readOnly = true)
  public UserStatsResponse getMyStats(String userId) {
    log.debug("[UserService] getMyStats() - START | userId: {}", userId);

    findUserById(userId);

    /*
        총 학습 시간은 study_time_log.duration_minutes 합계다.
    */
    int totalStudyMinutes =
        ((Number)
                entityManager
                    .createNativeQuery(
                        "SELECT COALESCE(SUM(duration_minutes), 0) "
                            + "FROM study_time_log WHERE user_id = :userId")
                    .setParameter("userId", userId)
                    .getSingleResult())
            .intValue();

    /* enum 을 패키지 경로로 하드코딩하면 리팩터링에 약해 JPQL 파라미터로 넘긴다. */
    long completedLessons =
        (long)
            entityManager
                .createQuery(
                    "SELECT COUNT(c) FROM Chapter c "
                        + "WHERE c.course.user.id = :userId AND c.status = :status")
                .setParameter("userId", userId)
                .setParameter("status", ChapterStatus.COMPLETED)
                .getSingleResult();

    /*
        연속 학습 일수는 study_time_log 의 일별 학습 날짜를 읽어 Java 에서 계산한다.
        NativeQuery 는 TypedQuery 를 지원하지 않아 wildcard List 로 받은 뒤 instanceof 로 변환한다.
        @SuppressWarnings 없이 컴파일 경고가 나지 않는다.
    */
    List<?> rawStudyDates =
        entityManager
            .createNativeQuery(
                "SELECT DISTINCT CAST(started_at AS DATE) AS study_date "
                    + "FROM study_time_log WHERE user_id = :userId "
                    + "ORDER BY study_date DESC")
            .setParameter("userId", userId)
            .getResultList();

    List<LocalDate> studyDates =
        rawStudyDates.stream()
            .filter(Date.class::isInstance)
            .map(d -> ((Date) d).toLocalDate())
            .toList();

    int streakDays = calculateStreakDays(studyDates);

    UserStatsResponse result =
        UserStatsResponse.builder()
            .totalStudyMinutes(totalStudyMinutes)
            .completedLessons((int) completedLessons)
            .streakDays(streakDays)
            .build();

    log.debug(
        "[UserService] getMyStats() - END | totalMinutes: {}, completedLessons: {}, streak: {}",
        totalStudyMinutes,
        completedLessons,
        streakDays);
    return result;
  }

  // 계정 삭제

  /**
   * 사용자 계정과 관련 데이터를 영구 삭제한다 (하드 삭제).
   *
   * <p>users(id)를 참조하는 모든 FK 에 ON DELETE CASCADE 가 걸려 있어 자식 row 는 DB 가 지운다. row 를 지우기
   * 전에 다음을 한다.
   *
   * <ol>
   *   <li>tutor 와의 순환 FK 를 끊으려고 users.current_tutor_id 를 NULL 로 바꾼다
   *   <li>S3 프로필 이미지를 지운다 (실패해도 계속 진행)
   *   <li>Redis 에 남은 텔레그램 연동 토큰을 지운다
   * </ol>
   */
  @Transactional
  public void deleteAccount(String userId) {
    log.info("[UserService] deleteAccount() - START | userId: {}", userId);

    User user = findUserById(userId);

    /* 순환 FK users.current_tutor_id → tutor(tutor_id) 를 끊는다. */
    if (user.getCurrentTutorId() != null) {
      entityManager
          .createNativeQuery("UPDATE users SET current_tutor_id = NULL WHERE id = :userId")
          .setParameter("userId", userId)
          .executeUpdate();
      entityManager.flush();
    }

    /* S3 프로필 이미지는 지워 보되 실패해도 계속한다. */
    String profileImageUrl = user.getProfileImageUrl();
    if (profileImageUrl != null && !profileImageUrl.isBlank()) {
      try {
        s3Service.delete(profileImageUrl);
      } catch (Exception e) {
        log.warn(
            "[UserService] deleteAccount() - S3 profile image deletion failed (non-fatal)"
                + " | url: {}, error: {}",
            LogMaskUtil.mask(profileImageUrl),
            e.getMessage());
      }
    }

    /* Redis 에 남은 텔레그램 연동 토큰을 지운다. */
    String linkToken = user.getTelegramLinkToken();
    if (linkToken != null && !linkToken.isBlank()) {
      try {
        redisTemplate.delete(TG_LINK_PREFIX + linkToken);
      } catch (Exception e) {
        log.warn(
            "[UserService] deleteAccount() - Redis token cleanup failed (non-fatal) | error: {}",
            e.getMessage());
      }
    }

    /* 자식 테이블은 ON DELETE CASCADE 로 함께 지워진다. */
    entityManager
        .createNativeQuery("DELETE FROM users WHERE id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();

    log.info("[UserService] deleteAccount() - END | userId: {}", userId);
  }

  // Private Helpers

  /**
   * 학습 날짜 목록에서 오늘/어제부터의 연속 학습 일수를 계산한다.
   *
   * @param studyDates 내림차순으로 정렬된 학습 날짜 목록
   */
  private int calculateStreakDays(List<LocalDate> studyDates) {
    if (studyDates == null || studyDates.isEmpty()) {
      return 0;
    }

    LocalDate today = LocalDate.now();
    LocalDate firstDate = studyDates.get(0);

    /* 오늘이나 어제에 학습 기록이 없으면 streak 0 */
    if (!firstDate.equals(today) && !firstDate.equals(today.minusDays(1))) {
      return 0;
    }

    int streak = 1;
    for (int i = 1; i < studyDates.size(); i++) {
      LocalDate current = studyDates.get(i);
      LocalDate previous = studyDates.get(i - 1);
      if (previous.minusDays(1).equals(current)) {
        streak++;
      } else {
        break;
      }
    }
    return streak;
  }

  /** 사용자 프로필 이미지를 업로드한다. 기존 이미지가 있으면 S3에서 삭제 후 교체. */
  @Transactional
  public UserProfileImageResponse uploadProfileImage(String userId, MultipartFile file) {
    log.debug("[UserService] uploadProfileImage() - START | userId: {}", userId);

    User user = findUserById(userId);
    validateProfileImageFile(file);

    String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
    if (ext != null) ext = ext.toLowerCase();
    String fileName = UUID.randomUUID() + "." + ext;

    String oldUrl = user.getProfileImageUrl();

    String url;
    try {
      url = s3Service.upload("user-profiles", fileName, file, file.getContentType());
    } catch (Exception e) {
      log.error(
          "[UserService] uploadProfileImage() - S3 업로드 실패 | userId: {}, error: {}",
          userId,
          e.getMessage());
      throw new CustomException(UserErrorCode.USER_012);
    }

    try {
      user.updateProfileImageUrl(url);
    } catch (Exception e) {
      /* 엔티티 갱신에 실패하면 방금 올린 S3 파일을 지운다. 이 삭제가 실패해도 원래 예외를 다시
      던져야 하므로 무시하되, S3 에 고아 파일이 남았음을 알 수 있게 경고 로그를 남긴다. */
      try {
        s3Service.delete(url);
      } catch (Exception rollbackEx) {
        log.warn(
            "[UserService] uploadProfileImage() - 롤백 S3 삭제 실패 (고아 파일 잔존 가능)" + " | url: {}, 원인: {}",
            LogMaskUtil.mask(url),
            rollbackEx.getMessage());
      }
      throw e;
    }

    if (oldUrl != null && !oldUrl.isBlank()) {
      try {
        s3Service.delete(oldUrl);
      } catch (Exception e) {
        log.warn(
            "[UserService] 기존 프로필 이미지 삭제 실패 (무시) | oldUrl: {}, error: {}",
            LogMaskUtil.mask(oldUrl),
            e.getMessage());
      }
    }

    log.debug(
        "[UserService] uploadProfileImage() - END | userId: {}, url: {}",
        userId,
        LogMaskUtil.mask(url));
    return UserProfileImageResponse.builder().profileImageUrl(url).build();
  }

  /** 사용자 프로필 이미지를 삭제한다. */
  @Transactional
  public void deleteProfileImage(String userId) {
    log.debug("[UserService] deleteProfileImage() - START | userId: {}", userId);

    User user = findUserById(userId);
    String oldUrl = user.getProfileImageUrl();
    user.clearProfileImageUrl();

    if (oldUrl != null && !oldUrl.isBlank()) {
      try {
        s3Service.delete(oldUrl);
      } catch (Exception e) {
        log.warn(
            "[UserService] 프로필 이미지 S3 삭제 실패 (무시) | oldUrl: {}, error: {}",
            LogMaskUtil.mask(oldUrl),
            e.getMessage());
      }
    }

    log.debug("[UserService] deleteProfileImage() - END | userId: {}", userId);
  }

  /** 프로필 이미지 파일의 존재·Content-Type·크기를 검증한다. */
  private void validateProfileImageFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new CustomException(UserErrorCode.USER_009);
    }
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
      throw new CustomException(UserErrorCode.USER_010);
    }
    if (file.getSize() > MAX_IMAGE_SIZE) {
      throw new CustomException(UserErrorCode.USER_011);
    }
  }

  /** userId로 사용자를 조회한다. 없으면 USER_001 예외를 던진다. */
  private User findUserById(String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(AuthErrorCode.USER_001));
  }

  /** 신규 사용자의 DEFAULT user_settings 를 생성하고 저장한다. */
  private UserSettings createDefaultSettings(User user) {
    UserSettings settings = UserSettings.defaultFor(user);
    return userSettingsRepository.save(settings);
  }
}
