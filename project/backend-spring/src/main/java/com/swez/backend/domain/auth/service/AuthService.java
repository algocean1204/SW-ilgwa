/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.swez.backend.domain.auth.dto.request.GoogleLoginRequest;
import com.swez.backend.domain.auth.dto.request.KakaoLoginRequest;
import com.swez.backend.domain.auth.dto.response.LoginResponse;
import com.swez.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.swez.backend.domain.auth.entity.OauthAccount;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.OauthProvider;
import com.swez.backend.domain.auth.exception.AuthErrorCode;
import com.swez.backend.domain.auth.mapper.AuthMapper;
import com.swez.backend.domain.auth.repository.OauthAccountRepository;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.global.auth.JwtProvider;
import com.swez.backend.global.config.properties.AuthCookieProperties;
import com.swez.backend.global.config.properties.JwtProperties;
import com.swez.backend.global.config.properties.OAuthProperties;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.exception.GlobalErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Google/Kakao 소셜 로그인과 토큰 갱신을 처리한다.
 *
 * <p>리프레시 토큰은 Redis 에 저장(RT:{SHA256(token)} → userId, TTL = refreshExpiryMs). HTTP-Only Cookie
 * ilkwa_refresh 로 전달하며, /auth/refresh 호출 시 Token Rotation 을 한다.
 *
 * <p>재사용 탐지: GETDEL 로 원자적으로 삭제한 뒤 USED_RT:{hash} 를 5분 TTL 로 남긴다. 그 사이 같은 토큰이 다시
 * 오면 AUTH_004 를 반환한다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

  private static final String REFRESH_TOKEN_PREFIX = "RT:";
  private static final String USED_REFRESH_TOKEN_PREFIX = "USED_RT:";
  private static final String COOKIE_NAME = "ilkwa_refresh";
  private static final long COOKIE_MAX_AGE_SECONDS = 14 * 24 * 60 * 60L;
  private static final long USED_REFRESH_TOKEN_TTL_MINUTES = 5L;
  private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
  private static final String KAKAO_USER_URL = "https://kapi.kakao.com/v2/user/me";
  private static final String DEMO_LOGIN_EMAIL = "demo@ilgwa.kr";
  private static final String DEMO_LOGIN_NICKNAME = "데모 사용자";

  private final UserRepository userRepository;
  private final OauthAccountRepository oauthAccountRepository;
  private final AuthMapper authMapper;
  private final JwtProvider jwtProvider;
  private final JwtProperties jwtProperties;
  private final OAuthProperties oAuthProperties;
  private final AuthCookieProperties authCookieProperties;
  private final RedisTemplate<String, String> redisTemplate;
  private final GoogleIdTokenVerifier googleIdTokenVerifier;
  private final RestTemplate restTemplate;

  @Value("${app.dev-login.enabled:false}")
  private boolean devLoginEnabled;

  /**
   * Google ID Token 로그인.
   *
   * <p>ID Token 검증 → 이메일 동의 확인 → 기존 OAuth 계정 조회 또는 신규 사용자 생성 → 토큰 발급.
   *
   * @param response 리프레시 쿠키를 설정할 HttpServletResponse
   * @return 액세스 토큰 및 사용자 정보
   */
  public LoginResponse loginWithGoogle(GoogleLoginRequest request, HttpServletResponse response) {
    log.debug("[AuthService] loginWithGoogle() - START");

    GoogleIdToken.Payload payload = verifyGoogleToken(request.idToken());
    String googleUserId = payload.getSubject();
    String email = payload.getEmail();

    /* 이메일 스코프가 없으면 거부한다. */
    if (!StringUtils.hasText(email)) {
      throw new CustomException(AuthErrorCode.AUTH_007);
    }

    User user = findOrCreateOauthUser(OauthProvider.GOOGLE, googleUserId, email);

    LoginResponse result = issueTokensAndSetCookie(user, response);

    log.debug("[AuthService] loginWithGoogle() - END | userId: {}", user.getId());
    return result;
  }

  /**
   * Kakao Authorization Code 로그인.
   *
   * <p>Authorization Code → Kakao Access Token 교환 → 사용자 정보 조회 → 이메일 동의 확인 → 신규 또는 기존 사용자 처리 → 토큰
   * 발급.
   *
   * @param response 리프레시 쿠키를 설정할 HttpServletResponse
   * @return 액세스 토큰 및 사용자 정보
   */
  public LoginResponse loginWithKakao(KakaoLoginRequest request, HttpServletResponse response) {
    log.debug("[AuthService] loginWithKakao() - START");

    /* Redirect URI 는 서버 환경변수 값을 쓴다. */
    String kakaoAccessToken =
        exchangeKakaoToken(
            request.authorizationCode(), oAuthProperties.getKakao().getRedirectUri());

    Map<String, Object> kakaoUserInfo = fetchKakaoUserInfo(kakaoAccessToken);
    String kakaoUserId = String.valueOf(kakaoUserInfo.get("id"));
    String email = extractKakaoEmail(kakaoUserInfo);

    /* 이메일 제공에 동의하지 않았으면 거부한다. */
    if (!StringUtils.hasText(email)) {
      throw new CustomException(AuthErrorCode.AUTH_007);
    }

    User user = findOrCreateOauthUser(OauthProvider.KAKAO, kakaoUserId, email);

    LoginResponse result = issueTokensAndSetCookie(user, response);

    log.debug("[AuthService] loginWithKakao() - END | userId: {}", user.getId());
    return result;
  }

  /**
   * 로컬 데모 로그인.
   *
   * <p>app.dev-login.enabled 가 true 일 때만 데모 사용자를 선택하거나 생성한 뒤 OAuth 로그인과 동일한 토큰·쿠키 발급 경로를 사용한다.
   *
   * @param response 리프레시 쿠키를 설정할 HttpServletResponse
   * @return 액세스 토큰 및 사용자 정보
   */
  public LoginResponse devLogin(HttpServletResponse response) {
    log.debug("[AuthService] devLogin() - START");

    /* 프로덕션에서 쓰이지 않게, 비활성 상태면 사용자 조회나 토큰 발급 전에 바로 막는다. */
    if (!devLoginEnabled) {
      throw new CustomException(AuthErrorCode.AUTH_008);
    }

    /* 데모 계정 이메일 → 첫 사용자 → 신규 데모 사용자 순서로 로그인 대상을 결정한다. */
    User user = findOrCreateDevUser();

    /* OAuth 로그인과 동일한 응답·리프레시 쿠키 발급 경로를 재사용한다. */
    LoginResponse result = issueTokensAndSetCookie(user, response);

    log.debug("[AuthService] devLogin() - END | userId: {}", user.getId());
    return result;
  }

  /**
   * 리프레시 토큰으로 액세스 토큰 갱신 (Token Rotation).
   *
   * <p>쿠키에서 토큰 추출 → USED_RT 재사용 탐지 → GETDEL 원자 삭제 → 사용자 조회 → USED_RT 보존 → 새 토큰 발급 → 쿠키 갱신.
   *
   * @param request 리프레시 쿠키가 포함된 HttpServletRequest
   * @param response 새 리프레시 쿠키를 설정할 HttpServletResponse
   * @return 새로 발급된 액세스 토큰
   */
  public TokenRefreshResponse refresh(HttpServletRequest request, HttpServletResponse response) {
    log.debug("[AuthService] refresh() - START");

    String refreshToken = extractRefreshTokenFromCookie(request);
    String tokenHash = hashToken(refreshToken);
    String redisKey = REFRESH_TOKEN_PREFIX + tokenHash;
    String usedKey = USED_REFRESH_TOKEN_PREFIX + tokenHash;

    /* USED_RT deny key 가 있으면 이미 쓴 토큰을 다시 보낸 것이다. */
    if (Boolean.TRUE.equals(redisTemplate.hasKey(usedKey))) {
      throw new CustomException(AuthErrorCode.AUTH_004);
    }

    /* GETDEL 로 조회와 삭제를 한 번의 원자 연산으로 처리한다. */
    String userId = redisTemplate.opsForValue().getAndDelete(redisKey);
    if (userId == null) {
      throw new CustomException(AuthErrorCode.AUTH_003);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(AuthErrorCode.USER_001));

    /* 사용된 토큰을 deny key 로 남겨 TTL 동안 재사용을 탐지한다. */
    redisTemplate
        .opsForValue()
        .set(usedKey, userId, USED_REFRESH_TOKEN_TTL_MINUTES, TimeUnit.MINUTES);

    String newAccessToken = jwtProvider.createAccessToken(user.getId());
    String newRefreshToken = jwtProvider.createRefreshToken(user.getId());
    saveRefreshToken(newRefreshToken, user.getId());
    setRefreshCookie(newRefreshToken, response);

    TokenRefreshResponse result = authMapper.toTokenRefreshResponse(newAccessToken);

    log.debug("[AuthService] refresh() - END | userId: {}", user.getId());
    return result;
  }

  /**
   * 로그아웃한다. 리프레시 토큰을 무효화하고 리프레시 쿠키를 제거한다.
   *
   * <p>만료된 액세스 토큰으로도 호출 가능해야 하므로 SecurityConfig 에서 permitAll 로 열어 둔다. 멱등하게 동작한다: 쿠키가 없거나 이미 무효화된
   * 토큰이어도 항상 쿠키 클리어 응답을 반환한다.
   *
   * @param request 리프레시 쿠키가 포함될 수 있는 HttpServletRequest
   * @param response 리프레시 쿠키를 maxAge=0 으로 제거할 HttpServletResponse
   */
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    log.debug("[AuthService] logout() - START");

    /* 쿠키가 없으면 토큰 무효화는 건너뛰고 쿠키만 지운다(멱등). */
    String refreshToken = resolveRefreshTokenFromCookie(request);

    /* refresh() 와 같은 SHA-256 해시로 Redis RT 키를 지운다.
     * 이미 만료되거나 회전돼 키가 없어도 delete 는 무해하므로 멱등하다. */
    if (refreshToken != null) {
      String redisKey = REFRESH_TOKEN_PREFIX + hashToken(refreshToken);
      redisTemplate.delete(redisKey);
    }

    /* setRefreshCookie 와 같은 속성에 maxAge=0 을 준 쿠키를 내려보내 지운다. */
    clearRefreshCookie(response);

    log.debug("[AuthService] logout() - END");
  }

  /**
   * 회원 탈퇴(소프트 삭제). 사용자 상태를 WITHDRAWN 으로 바꾸고 바로 로그아웃시킨다.
   *
   * <p>데이터는 보존하되 재로그인·재가입을 차단한다(findOrCreateOauthUser 에서 AUTH_009). 리프레시 토큰을 무효화하고 쿠키를 제거해 세션을
   * 종료한다. 잔여 액세스 토큰은 만료까지 유효하나 단명이며, 리프레시가 막혀 갱신되지 않는다.
   *
   * @param request 리프레시 쿠키가 포함될 수 있는 HttpServletRequest
   * @param response 리프레시 쿠키를 maxAge=0 으로 제거할 HttpServletResponse
   */
  public void withdraw(String userId, HttpServletRequest request, HttpServletResponse response) {
    log.debug("[AuthService] withdraw() - START | userId: {}", userId);

    User user =
        userRepository.findById(userId).orElseThrow(() -> new CustomException(AuthErrorCode.USER_001));

    /* 이미 탈퇴한 회원이면 그대로 둔다(멱등). */
    if (!user.isWithdrawn()) {
      user.withdraw();
    }

    /* logout 과 같이 리프레시 토큰을 무효화하고 쿠키를 지워 세션을 끝낸다. */
    String refreshToken = resolveRefreshTokenFromCookie(request);
    if (refreshToken != null) {
      redisTemplate.delete(REFRESH_TOKEN_PREFIX + hashToken(refreshToken));
    }
    clearRefreshCookie(response);

    log.debug("[AuthService] withdraw() - END | userId: {}", userId);
  }

  // 내부 헬퍼

  /** 요청 쿠키에서 ilkwa_refresh 값을 추출한다. 쿠키가 없으면 예외 대신 null 을 반환해 로그아웃을 멱등하게 한다. */
  private String resolveRefreshTokenFromCookie(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return null;
    }
    return Arrays.stream(request.getCookies())
        .filter(c -> COOKIE_NAME.equals(c.getName()))
        .findFirst()
        .map(jakarta.servlet.http.Cookie::getValue)
        .orElse(null);
  }

  /**
   * 리프레시 쿠키를 제거한다. setRefreshCookie 와 동일한 path·secure·httpOnly·sameSite·domain 속성에 maxAge=0 을 적용해야
   * 브라우저가 기존 쿠키를 정확히 덮어쓰고 삭제한다. 속성이 하나라도 다르면 별개 쿠키로 인식되어 삭제되지 않는다.
   */
  private void clearRefreshCookie(HttpServletResponse response) {
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(authCookieProperties.isSecure())
            .sameSite(authCookieProperties.getSameSite())
            .path("/api/auth/refresh")
            .maxAge(0);

    /* setRefreshCookie 와 같이 domain 이 설정된 경우에만 적용해 빈 Domain= 헤더가 붙지 않게 한다. */
    if (StringUtils.hasText(authCookieProperties.getDomain())) {
      builder = builder.domain(authCookieProperties.getDomain());
    }

    response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
  }

  /** 액세스·리프레시 토큰을 발급하고 리프레시 쿠키를 설정한 뒤 LoginResponse 를 반환한다. */
  private LoginResponse issueTokensAndSetCookie(User user, HttpServletResponse response) {
    String accessToken = jwtProvider.createAccessToken(user.getId());
    String refreshToken = jwtProvider.createRefreshToken(user.getId());
    saveRefreshToken(refreshToken, user.getId());
    setRefreshCookie(refreshToken, response);
    return authMapper.toLoginResponse(user, accessToken);
  }

  /** 리프레시 토큰을 SHA-256 해시 후 Redis 에 저장한다. TTL 은 refreshExpiryMs 설정값을 따른다. */
  private void saveRefreshToken(String refreshToken, String userId) {
    String key = REFRESH_TOKEN_PREFIX + hashToken(refreshToken);
    redisTemplate
        .opsForValue()
        .set(key, userId, jwtProperties.getRefreshExpiryMs(), TimeUnit.MILLISECONDS);
  }

  /**
   * ResponseCookie 를 사용해 HTTP-Only 쿠키를 응답 헤더에 추가한다. secure·sameSite·domain 은 환경변수로 제어한다.
   *
   * <p>쿠키 path 를 "/api/auth/refresh" 로 고정한다. Spring context-path 가 /api 이므로 브라우저가 refresh 엔드포인트를
   * "/api/auth/refresh" 로 호출하는데, 쿠키 path 가 "/auth/refresh" 이면 브라우저가 path prefix 불일치로 쿠키를 전송하지 않아
   * refresh 가 실패한다.
   */
  private void setRefreshCookie(String refreshToken, HttpServletResponse response) {
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(COOKIE_NAME, refreshToken)
            .httpOnly(true)
            .secure(authCookieProperties.isSecure())
            .sameSite(authCookieProperties.getSameSite())
            .path("/api/auth/refresh")
            .maxAge(COOKIE_MAX_AGE_SECONDS);

    /* domain 이 설정된 경우에만 적용해 빈 Domain= 헤더가 붙지 않게 한다. */
    if (StringUtils.hasText(authCookieProperties.getDomain())) {
      builder = builder.domain(authCookieProperties.getDomain());
    }

    response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
  }

  /** 토큰을 SHA-256 으로 해싱하여 16진수 문자열로 반환한다. Redis 키 생성에 사용한다. */
  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        String h = Integer.toHexString(0xff & b);
        if (h.length() == 1) hex.append('0');
        hex.append(h);
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new CustomException(GlobalErrorCode.SYSTEM_001);
    }
  }

  /** 요청 쿠키 목록에서 ilkwa_refresh 쿠키 값을 추출한다. 없으면 AUTH_003 예외를 발생시킨다. */
  private String extractRefreshTokenFromCookie(HttpServletRequest request) {
    if (request.getCookies() == null) {
      throw new CustomException(AuthErrorCode.AUTH_003);
    }
    return Arrays.stream(request.getCookies())
        .filter(c -> COOKIE_NAME.equals(c.getName()))
        .findFirst()
        .map(jakarta.servlet.http.Cookie::getValue)
        .orElseThrow(() -> new CustomException(AuthErrorCode.AUTH_003));
  }

  /** (provider, providerUserId) 로 기존 OAuth 계정을 조회한다. 없으면 신규 사용자와 OAuth 계정을 함께 생성한다. */
  private User findOrCreateOauthUser(OauthProvider provider, String providerUserId, String email) {
    return oauthAccountRepository
        .findByProviderAndProviderUserId(provider, providerUserId)
        .map(OauthAccount::getUser)
        .map(this::rejectIfWithdrawn)
        .orElseGet(() -> createOauthUser(provider, providerUserId, email));
  }

  /** 탈퇴(WITHDRAWN) 계정의 재로그인을 차단한다. 정상 계정이면 그대로 통과시킨다. */
  private User rejectIfWithdrawn(User user) {
    if (user.isWithdrawn()) {
      throw new CustomException(AuthErrorCode.AUTH_009);
    }
    return user;
  }

  /** 로컬 데모 로그인 대상을 결정한다. demo@ilgwa.kr, 첫 사용자, 신규 데모 사용자 순서로 선택한다. */
  private User findOrCreateDevUser() {
    return userRepository
        .findByEmail(DEMO_LOGIN_EMAIL)
        .or(() -> findFirstUser())
        .orElseGet(this::createDevUser);
  }

  /** 서비스에 사용자가 있으면 가장 먼저 생성된 사용자를 데모 로그인 대상으로 반환한다. */
  private Optional<User> findFirstUser() {
    PageRequest firstUser =
        PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by("id")));
    return userRepository.findAll(firstUser).getContent().stream().findFirst();
  }

  /** 데모 계정이 없고 사용자도 비어 있을 때 로컬 데모 전용 사용자를 생성한다. */
  private User createDevUser() {
    User user = authMapper.toDevUser(DEMO_LOGIN_EMAIL, DEMO_LOGIN_NICKNAME);
    return userRepository.save(user);
  }

  /** 신규 OAuth 사용자를 생성하고 oauth_account 를 연결한다. 이메일이 이미 존재하면 USER_002 예외를 발생시킨다. */
  private User createOauthUser(OauthProvider provider, String providerUserId, String email) {
    if (userRepository.existsByEmail(email)) {
      throw new CustomException(AuthErrorCode.USER_002);
    }
    String nickname = email.split("@")[0];
    User user = authMapper.toOauthUser(email, nickname);
    User savedUser = userRepository.save(user);
    OauthAccount oauthAccount =
        authMapper.toOauthAccount(savedUser, provider, providerUserId, email);
    oauthAccountRepository.save(oauthAccount);
    return savedUser;
  }

  /** Google ID Token 서명과 audience 를 검증하고 Payload 를 반환한다. 검증 실패 시 AUTH_005 예외를 발생시킨다. */
  private GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
    try {
      GoogleIdToken idToken = googleIdTokenVerifier.verify(idTokenString);
      if (idToken == null) {
        throw new CustomException(AuthErrorCode.AUTH_005);
      }
      return idToken.getPayload();
    } catch (GeneralSecurityException | IOException e) {
      log.warn("[AuthService] Google 토큰 검증 실패: {}", e.getMessage());
      throw new CustomException(AuthErrorCode.AUTH_005);
    }
  }

  /** Authorization Code 를 Kakao 토큰 엔드포인트에 전송해 Access Token 을 발급받는다. */
  private String exchangeKakaoToken(String authorizationCode, String redirectUri) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "authorization_code");
    body.add("client_id", oAuthProperties.getKakao().getClientId());
    body.add("client_secret", oAuthProperties.getKakao().getClientSecret());
    body.add("redirect_uri", redirectUri);
    body.add("code", authorizationCode);

    try {
      /* ParameterizedTypeReference 로 raw Map 대신 Map<String, Object> 로 받는다. */
      ResponseEntity<Map<String, Object>> tokenResponse =
          restTemplate.exchange(
              KAKAO_TOKEN_URL,
              HttpMethod.POST,
              new HttpEntity<>(body, headers),
              new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, Object> responseBody = tokenResponse.getBody();
      if (responseBody == null || !responseBody.containsKey("access_token")) {
        throw new CustomException(AuthErrorCode.AUTH_006);
      }
      return (String) responseBody.get("access_token");
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.warn("[AuthService] Kakao 토큰 교환 실패: {}", e.getMessage());
      throw new CustomException(AuthErrorCode.AUTH_006);
    }
  }

  /** Kakao Access Token 으로 사용자 정보 API 를 호출한다. */
  private Map<String, Object> fetchKakaoUserInfo(String kakaoAccessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken);

    try {
      ResponseEntity<Map<String, Object>> userResponse =
          restTemplate.exchange(
              KAKAO_USER_URL,
              HttpMethod.GET,
              new HttpEntity<>(headers),
              new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, Object> responseBody = userResponse.getBody();
      if (responseBody == null) {
        throw new CustomException(AuthErrorCode.AUTH_006);
      }
      return responseBody;
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.warn("[AuthService] Kakao 사용자 정보 조회 실패: {}", e.getMessage());
      throw new CustomException(AuthErrorCode.AUTH_006);
    }
  }

  /**
   * Kakao 사용자 정보 응답에서 이메일을 추출한다. 이메일이 없으면 null 을 반환한다. 중첩 Map 은 instanceof 패턴 매칭으로
   * unchecked 캐스트 없이 꺼낸다.
   */
  private String extractKakaoEmail(Map<String, Object> kakaoUserInfo) {
    if (!(kakaoUserInfo.get("kakao_account") instanceof Map<?, ?> rawAccount)) {
      return null;
    }
    Object email = rawAccount.get("email");
    return email instanceof String s ? s : null;
  }
}
