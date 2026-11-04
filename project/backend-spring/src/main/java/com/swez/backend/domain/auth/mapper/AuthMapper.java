/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.mapper;

import org.springframework.stereotype.Component;

import com.swez.backend.domain.auth.dto.response.LoginResponse;
import com.swez.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.swez.backend.domain.auth.entity.OauthAccount;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.OauthProvider;
import com.swez.backend.global.util.IdGenerator;

/** 인증 도메인 엔티티와 DTO 를 변환한다. */
@Component
public class AuthMapper {

  /**
   * OAuth 사용자를 User 엔티티로 변환 (비밀번호 없음).
   *
   * @param nickname 초기 닉네임 (이메일 앞부분 사용)
   * @return 생성된 User 엔티티 (미저장)
   */
  public User toOauthUser(String email, String nickname) {
    return User.builder().id(IdGenerator.user()).email(email).nickname(nickname).build();
  }

  /**
   * 로컬 데모 로그인용 User 엔티티로 변환한다.
   *
   * @return 생성된 User 엔티티 (미저장)
   */
  public User toDevUser(String email, String nickname) {
    return User.builder().id(IdGenerator.user()).email(email).nickname(nickname).build();
  }

  /**
   * User 와 제공자 정보를 OauthAccount 엔티티로 변환.
   *
   * @param providerEmail 제공자로부터 받은 이메일 (없을 수 있음)
   * @return 생성된 OauthAccount 엔티티 (미저장)
   */
  public OauthAccount toOauthAccount(
      User user, OauthProvider provider, String providerUserId, String providerEmail) {
    return OauthAccount.builder()
        .id(IdGenerator.oauthAccount())
        .user(user)
        .provider(provider)
        .providerUserId(providerUserId)
        .email(providerEmail)
        .build();
  }

  /** User 엔티티와 액세스 토큰을 LoginResponse 로 변환. */
  public LoginResponse toLoginResponse(User user, String accessToken) {
    return LoginResponse.builder()
        .accessToken(accessToken)
        .userId(user.getId())
        .email(user.getEmail())
        .nickname(user.getNickname())
        .onboardingCompleted(user.getOnboardingCompleted())
        .subscriptionStatus(user.getSubscriptionStatus())
        .role(user.getRole())
        .build();
  }

  /** 새 액세스 토큰을 TokenRefreshResponse 로 변환. */
  public TokenRefreshResponse toTokenRefreshResponse(String accessToken) {
    return TokenRefreshResponse.builder().accessToken(accessToken).build();
  }
}
