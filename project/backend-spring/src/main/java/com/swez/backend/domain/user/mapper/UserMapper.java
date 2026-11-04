/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.mapper;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.subscription.dto.response.CurrentSubscriptionResponse;
import com.swez.backend.domain.tutor.entity.Tutor;
import com.swez.backend.domain.user.dto.response.OnboardingResponse;
import com.swez.backend.domain.user.dto.response.SettingsResponse;
import com.swez.backend.domain.user.dto.response.TelegramPreferencesResponse;
import com.swez.backend.domain.user.dto.response.TelegramStatusResponse;
import com.swez.backend.domain.user.dto.response.UserProfileResponse;
import com.swez.backend.domain.user.entity.TelegramAccount;
import com.swez.backend.domain.user.entity.UserSettings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 사용자 도메인 엔티티 ↔ DTO 변환 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserMapper {

  private static final Map<String, Object> DEFAULT_NOTIFICATION_SETTINGS =
      Map.of("lesson", true, "assignment", true, "community", true, "marketing", false);

  private static final Map<String, Object> DEFAULT_ACCESSIBILITY_SETTINGS =
      Map.of("highContrast", false, "keyboardNavigation", false);

  private final ObjectMapper objectMapper;

  /**
   * User 엔티티 + 연관 데이터를 UserProfileResponse 로 변환.
   *
   * <p>구독 상태는 User 엔티티 캐시 필드 대신 SubscriptionService 가 반환한 CurrentSubscriptionResponse 를 기준으로
   * 쓴다. 그래서 profile 엔드포인트와 subscription 엔드포인트의 플랜 정보가 같다.
   *
   * @param settings 사용자 설정 (null이면 DEFAULT 사용)
   * @param telegram 텔레그램 계정 (null이면 미연동)
   * @param subscription SubscriptionService.getCurrentSubscription() 반환값
   */
  public UserProfileResponse toProfileResponse(
      User user,
      UserSettings settings,
      TelegramAccount telegram,
      CurrentSubscriptionResponse subscription) {
    return UserProfileResponse.builder()
        .id(user.getId())
        .email(user.getEmail())
        .nickname(user.getNickname())
        .profileImageUrl(user.getProfileImageUrl())
        .grade(user.getGradeLevel())
        .major(user.getMajor())
        .education(user.getEducation())
        .interests(parseStringList(user.getInterests()))
        .learningGoal(user.getLearningGoal())
        .preferredTone(user.getPreferredTone())
        .subscriptionStatus(resolveSubscriptionStatus(subscription))
        .subscriptionExpiresAt(subscription.endsAt())
        .xp(0) // TODO: user_game_state 테이블 연동 완료 후 실제 값으로 교체할 것
        .level(0) // TODO: user_game_state 테이블 연동 완료 후 실제 값으로 교체할 것
        .points(0) // TODO: user_game_state 테이블 연동 완료 후 실제 값으로 교체할 것
        .telegramLinked(telegram != null && Boolean.TRUE.equals(telegram.getIsActive()))
        .onboardingCompleted(Boolean.TRUE.equals(user.getOnboardingCompleted()))
        .createdAt(user.getCreatedAt())
        .settings(toSettingsResponse(user))
        .build();
  }

  /**
   * CurrentSubscriptionResponse의 planCode를 SubscriptionStatus enum 으로 변환한다.
   *
   * <p>알 수 없는 planCode는 FREE 로 폴백해 프로필 조회가 멈추지 않게 한다.
   */
  private SubscriptionStatus resolveSubscriptionStatus(CurrentSubscriptionResponse subscription) {
    try {
      return SubscriptionStatus.fromDbValue(subscription.planCode());
    } catch (IllegalArgumentException e) {
      log.warn("[UserMapper] 알 수 없는 planCode: '{}' — FREE 로 폴백", subscription.planCode());
      return SubscriptionStatus.FREE;
    }
  }

  /**
   * 텔레그램 계정을 TelegramStatusResponse 로 변환.
   *
   * @param telegram 텔레그램 계정 (null이면 미연동)
   */
  public TelegramStatusResponse toTelegramStatusResponse(TelegramAccount telegram) {
    if (telegram == null || !Boolean.TRUE.equals(telegram.getIsActive())) {
      return TelegramStatusResponse.builder().linked(false).build();
    }
    return TelegramStatusResponse.builder()
        .linked(true)
        .telegramChatId(telegram.getTelegramChatId())
        .telegramUsername(telegram.getTelegramUsername())
        .linkedAt(telegram.getLinkedAt())
        .lastActiveAt(telegram.getLastInteractionAt())
        .build();
  }

  /** UserSettings 의 텔레그램 자동 전송 옵션을 TelegramPreferencesResponse 로 변환. */
  public TelegramPreferencesResponse toTelegramPreferencesResponse(UserSettings settings) {
    return TelegramPreferencesResponse.builder()
        .noteEnabled(Boolean.TRUE.equals(settings.getTelegramNoteEnabled()))
        .quizEnabled(Boolean.TRUE.equals(settings.getTelegramQuizEnabled()))
        .friendEnabled(Boolean.TRUE.equals(settings.getTelegramFriendEnabled()))
        .build();
  }

  /** Tutor 프리셋 목록을 OnboardingResponse 로 변환. */
  public OnboardingResponse toOnboardingResponse(List<Tutor> tutors) {
    List<OnboardingResponse.RecommendedTutorDto> recommended =
        tutors.stream()
            .limit(3)
            .map(
                t ->
                    OnboardingResponse.RecommendedTutorDto.builder()
                        .id(t.getTutorId())
                        .name(t.getName())
                        .tagline(t.getTagline())
                        .profileImageUrl(t.getProfileImageUrl())
                        .build())
            .toList();

    return OnboardingResponse.builder()
        .onboardingCompleted(true)
        .recommendedTutors(recommended)
        .build();
  }

  /** List<String> 을 JSON 배열 문자열로 직렬화한다. null 이면 null 을 반환한다. */
  public String serializeStringList(List<String> list) {
    if (list == null) return null;
    try {
      return objectMapper.writeValueAsString(list);
    } catch (JsonProcessingException e) {
      log.warn("[UserMapper] interests 직렬화 실패: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 기존 JSONB 문자열에 요청 Map을 부분 병합한다.
   *
   * <p>기존 JSON이 null이면 defaultValues를 기반으로 병합한다.
   *
   * @param updates 덮어쓸 키-값 쌍
   * @param defaultValues 기존 JSON 이 null일 때 사용할 기본값
   */
  public String mergeJson(
      String existingJson, Map<String, Object> updates, Map<String, Object> defaultValues) {
    try {
      Map<String, Object> base =
          existingJson != null
              ? objectMapper.readValue(existingJson, new TypeReference<>() {})
              : new java.util.HashMap<>(defaultValues);
      updates.forEach(
          (k, v) -> {
            if (v != null) base.put(k, v);
          });
      return objectMapper.writeValueAsString(base);
    } catch (JsonProcessingException e) {
      log.warn("[UserMapper] JSONB merge 실패: {}", e.getMessage());
      return existingJson;
    }
  }

  /** users.accessibility_settings / notification_settings 를 SettingsResponse 로 변환. */
  private SettingsResponse toSettingsResponse(User user) {
    Map<String, Object> accessibility = parseMap(user.getAccessibilitySettings());
    Map<String, Object> notification = parseMap(user.getNotificationSettings());

    SettingsResponse.NotificationsResponse notifications =
        SettingsResponse.NotificationsResponse.builder()
            .lesson(getBool(notification, "lesson", true))
            .assignment(getBool(notification, "assignment", true))
            .community(getBool(notification, "community", true))
            .marketing(getBool(notification, "marketing", false))
            .build();

    return SettingsResponse.builder()
        .dailyStudyGoalMinutes(
            user.getDailyStudyGoalMinutes() != null ? user.getDailyStudyGoalMinutes() : 30)
        .highContrast(getBool(accessibility, "highContrast", false))
        .keyboardNavigation(getBool(accessibility, "keyboardNavigation", false))
        .notifications(notifications)
        .build();
  }

  private List<String> parseStringList(String json) {
    if (json == null) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      log.warn("[UserMapper] interests 역직렬화 실패: {}", e.getMessage());
      return List.of();
    }
  }

  private Map<String, Object> parseMap(String json) {
    if (json == null) return Map.of();
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      log.warn("[UserMapper] JSONB 역직렬화 실패: {}", e.getMessage());
      return Map.of();
    }
  }

  private boolean getBool(Map<String, Object> map, String key, boolean defaultValue) {
    Object val = map.get(key);
    if (val instanceof Boolean b) return b;
    return defaultValue;
  }

  /** 알림 설정 기본값 */
  public Map<String, Object> defaultNotificationSettings() {
    return DEFAULT_NOTIFICATION_SETTINGS;
  }

  /** 접근성 설정 기본값 */
  public Map<String, Object> defaultAccessibilitySettings() {
    return DEFAULT_ACCESSIBILITY_SETTINGS;
  }
}
