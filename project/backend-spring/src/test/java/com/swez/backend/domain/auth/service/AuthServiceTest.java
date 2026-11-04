/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
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
import com.swez.backend.domain.auth.enums.UserStatus;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.exception.AuthErrorCode;
import com.swez.backend.domain.auth.mapper.AuthMapper;
import com.swez.backend.domain.auth.repository.OauthAccountRepository;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.global.auth.JwtProvider;
import com.swez.backend.global.config.properties.AuthCookieProperties;
import com.swez.backend.global.config.properties.JwtProperties;
import com.swez.backend.global.config.properties.OAuthProperties;
import com.swez.backend.global.exception.CustomException;

@DisplayName("AuthService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private OauthAccountRepository oauthAccountRepository;
  @Mock private AuthMapper authMapper;
  @Mock private JwtProvider jwtProvider;
  @Mock private JwtProperties jwtProperties;
  @Mock private OAuthProperties oAuthProperties;
  @Mock private RedisTemplate<String, String> redisTemplate;
  @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;
  @Mock private RestTemplate restTemplate;

  @InjectMocks private AuthService authService;

  private ValueOperations<String, String> valueOps;
  private HttpServletResponse mockResponse;
  private HttpServletRequest mockRequest;

  private static final String TEST_USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String TEST_EMAIL = "test@example.com";
  private static final String TEST_NICKNAME = "테스터";
  private static final String TEST_ACCESS_TOKEN = "access.token.test";
  private static final String TEST_REFRESH_TOKEN = "refresh.token.test";
  private static final String GOOGLE_UID = "google_uid_123";
  private static final String KAKAO_UID = "123456789";
  private static final String DEMO_EMAIL = "demo@ilgwa.kr";
  private static final String DEMO_NICKNAME = "데모 사용자";

  @BeforeEach
  void setUp() {
    valueOps = mock(ValueOperations.class);
    mockResponse = mock(HttpServletResponse.class);
    mockRequest = mock(HttpServletRequest.class);
    given(redisTemplate.opsForValue()).willReturn(valueOps);
    given(jwtProperties.getRefreshExpiryMs()).willReturn(1_209_600_000L);

    // 쿠키 헤더 검증이 목적이 아니라서 AuthCookieProperties는 실제 인스턴스를 넣는다
    AuthCookieProperties cookieProperties = new AuthCookieProperties();
    ReflectionTestUtils.setField(authService, "authCookieProperties", cookieProperties);
    ReflectionTestUtils.setField(authService, "devLoginEnabled", false);
  }

  /** 테스트용 User 엔티티를 빌더로 생성한다. */
  private User makeUser() {
    return makeUser(TEST_USER_ID, TEST_EMAIL, TEST_NICKNAME);
  }

  /** 테스트용 User 엔티티를 원하는 식별자와 프로필로 생성한다. */
  private User makeUser(String id, String email, String nickname) {
    return User.builder()
        .id(id)
        .email(email)
        .nickname(nickname)
        .onboardingCompleted(false)
        .subscriptionStatus(SubscriptionStatus.FREE)
        .build();
  }

  /** 테스트용 LoginResponse 를 생성한다. */
  private LoginResponse makeLoginResponse() {
    return makeLoginResponse(makeUser());
  }

  /** User 엔티티 값이 그대로 반영된 테스트용 LoginResponse 를 생성한다. */
  private LoginResponse makeLoginResponse(User user) {
    return LoginResponse.builder()
        .accessToken(TEST_ACCESS_TOKEN)
        .userId(user.getId())
        .email(user.getEmail())
        .nickname(user.getNickname())
        .onboardingCompleted(user.getOnboardingCompleted())
        .subscriptionStatus(user.getSubscriptionStatus())
        .role(user.getRole())
        .build();
  }

  /** loginWithGoogle/Kakao 성공 경로에서 쓰는 토큰 발급 공통 스텁 */
  private void stubTokenIssuance(User user) {
    given(jwtProvider.createAccessToken(user.getId())).willReturn(TEST_ACCESS_TOKEN);
    given(jwtProvider.createRefreshToken(user.getId())).willReturn(TEST_REFRESH_TOKEN);
    given(authMapper.toLoginResponse(user, TEST_ACCESS_TOKEN)).willReturn(makeLoginResponse(user));
  }

  @Nested
  @DisplayName("loginWithGoogle()")
  class GoogleLogin {

    private GoogleIdToken.Payload makeGooglePayload() {
      GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
      payload.setSubject(GOOGLE_UID);
      payload.setEmail(TEST_EMAIL);
      return payload;
    }

    @Test
    @DisplayName("신규 Google 사용자 — User와 OauthAccount 가 저장되고 LoginResponse 를 반환한다")
    void loginWithGoogle_newUser_savesUserAndOauthAccount() throws Exception {
      // given
      GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
      User user = makeUser();
      OauthAccount oauthAccount = mock(OauthAccount.class);

      given(googleIdTokenVerifier.verify("valid_token")).willReturn(mockIdToken);
      given(mockIdToken.getPayload()).willReturn(makeGooglePayload());
      given(
              oauthAccountRepository.findByProviderAndProviderUserId(
                  OauthProvider.GOOGLE, GOOGLE_UID))
          .willReturn(Optional.empty());
      given(userRepository.existsByEmail(TEST_EMAIL)).willReturn(false);
      given(authMapper.toOauthUser(TEST_EMAIL, "test")).willReturn(user);
      given(userRepository.save(user)).willReturn(user);
      given(authMapper.toOauthAccount(user, OauthProvider.GOOGLE, GOOGLE_UID, TEST_EMAIL))
          .willReturn(oauthAccount);
      stubTokenIssuance(user);

      // when
      LoginResponse result =
          authService.loginWithGoogle(new GoogleLoginRequest("valid_token"), mockResponse);

      // then
      assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
      then(userRepository).should().save(user);
      then(oauthAccountRepository).should().save(oauthAccount);
    }

    @Test
    @DisplayName("기존 Google 사용자 — 저장 없이 기존 User 로 LoginResponse 를 반환한다")
    void loginWithGoogle_existingUser_returnsLoginResponseWithoutSave() throws Exception {
      // given
      GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
      User existingUser = makeUser();
      OauthAccount existingAccount = mock(OauthAccount.class);

      given(googleIdTokenVerifier.verify("valid_token")).willReturn(mockIdToken);
      given(mockIdToken.getPayload()).willReturn(makeGooglePayload());
      given(
              oauthAccountRepository.findByProviderAndProviderUserId(
                  OauthProvider.GOOGLE, GOOGLE_UID))
          .willReturn(Optional.of(existingAccount));
      given(existingAccount.getUser()).willReturn(existingUser);
      stubTokenIssuance(existingUser);

      // when
      LoginResponse result =
          authService.loginWithGoogle(new GoogleLoginRequest("valid_token"), mockResponse);

      // then
      assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
      then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("유효하지 않은 ID Token — verifier 가 null 을 반환하면 AUTH_005 예외를 던진다")
    void loginWithGoogle_invalidToken_throwsAuth005() throws Exception {
      // given
      given(googleIdTokenVerifier.verify("invalid_token")).willReturn(null);

      // when & then
      assertThatThrownBy(
              () ->
                  authService.loginWithGoogle(
                      new GoogleLoginRequest("invalid_token"), mockResponse))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.AUTH_005));
    }

    @Test
    @DisplayName("Google 검증 중 예외 발생 — GeneralSecurityException 이면 AUTH_005 예외를 던진다")
    void loginWithGoogle_verificationException_throwsAuth005() throws Exception {
      // given
      given(googleIdTokenVerifier.verify(anyString()))
          .willThrow(new GeneralSecurityException("서명 검증 실패"));

      // when & then
      assertThatThrownBy(
              () -> authService.loginWithGoogle(new GoogleLoginRequest("any_token"), mockResponse))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.AUTH_005));
    }

    @Test
    @DisplayName("Google payload 이메일 누락 — AUTH_007 예외를 던진다")
    void loginWithGoogle_noEmail_throwsAuth007() throws Exception {
      // given
      GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
      GoogleIdToken.Payload payloadWithoutEmail = new GoogleIdToken.Payload();
      payloadWithoutEmail.setSubject(GOOGLE_UID);
      // email 미설정 → null

      given(googleIdTokenVerifier.verify("token_no_email")).willReturn(mockIdToken);
      given(mockIdToken.getPayload()).willReturn(payloadWithoutEmail);

      // when & then
      assertThatThrownBy(
              () ->
                  authService.loginWithGoogle(
                      new GoogleLoginRequest("token_no_email"), mockResponse))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.AUTH_007));
    }
  }

  @Nested
  @DisplayName("loginWithKakao()")
  class KakaoLogin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubKakaoApis(String kakaoUid, String email) {
      OAuthProperties.Kakao kakaoProps = mock(OAuthProperties.Kakao.class);
      given(oAuthProperties.getKakao()).willReturn(kakaoProps);
      given(kakaoProps.getClientId()).willReturn("test_client_id");
      given(kakaoProps.getClientSecret()).willReturn("test_secret");
      given(kakaoProps.getRedirectUri()).willReturn("http://localhost:3000/oauth/kakao");

      // 프로덕션은 토큰 교환·사용자 정보 조회 모두 exchange(ParameterizedTypeReference) 오버로드를 사용한다.
      // POST/GET HttpMethod 로 두 호출을 구분한다.
      ResponseEntity<Map> tokenResponse = mock(ResponseEntity.class);
      given(tokenResponse.getBody()).willReturn(Map.of("access_token", "kakao_access_token"));
      given(
              restTemplate.exchange(
                  anyString(),
                  eq(HttpMethod.POST),
                  any(),
                  any(ParameterizedTypeReference.class)))
          .willReturn(tokenResponse);

      ResponseEntity<Map> userResponse = mock(ResponseEntity.class);
      Map<String, Object> kakaoAccount = email != null ? Map.of("email", email) : Map.of();
      given(userResponse.getBody())
          .willReturn(Map.of("id", Long.parseLong(kakaoUid), "kakao_account", kakaoAccount));
      given(
              restTemplate.exchange(
                  anyString(),
                  eq(HttpMethod.GET),
                  any(),
                  any(ParameterizedTypeReference.class)))
          .willReturn(userResponse);
    }

    @Test
    @DisplayName("신규 Kakao 사용자 — User와 OauthAccount 가 저장되고 LoginResponse 를 반환한다")
    void loginWithKakao_newUser_savesUserAndOauthAccount() {
      // given
      User user = makeUser();
      OauthAccount oauthAccount = mock(OauthAccount.class);
      stubKakaoApis(KAKAO_UID, TEST_EMAIL);

      given(oauthAccountRepository.findByProviderAndProviderUserId(OauthProvider.KAKAO, KAKAO_UID))
          .willReturn(Optional.empty());
      given(userRepository.existsByEmail(TEST_EMAIL)).willReturn(false);
      given(authMapper.toOauthUser(TEST_EMAIL, "test")).willReturn(user);
      given(userRepository.save(user)).willReturn(user);
      given(authMapper.toOauthAccount(user, OauthProvider.KAKAO, KAKAO_UID, TEST_EMAIL))
          .willReturn(oauthAccount);
      stubTokenIssuance(user);

      // when
      LoginResponse result =
          authService.loginWithKakao(new KakaoLoginRequest("auth_code"), mockResponse);

      // then
      assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
      then(userRepository).should().save(user);
      then(oauthAccountRepository).should().save(oauthAccount);
    }

    @Test
    @DisplayName("기존 Kakao 사용자 — 저장 없이 기존 User 로 LoginResponse 를 반환한다")
    void loginWithKakao_existingUser_returnsLoginResponseWithoutSave() {
      // given
      User existingUser = makeUser();
      OauthAccount existingAccount = mock(OauthAccount.class);
      stubKakaoApis(KAKAO_UID, TEST_EMAIL);

      given(oauthAccountRepository.findByProviderAndProviderUserId(OauthProvider.KAKAO, KAKAO_UID))
          .willReturn(Optional.of(existingAccount));
      given(existingAccount.getUser()).willReturn(existingUser);
      stubTokenIssuance(existingUser);

      // when
      LoginResponse result =
          authService.loginWithKakao(new KakaoLoginRequest("auth_code"), mockResponse);

      // then
      assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
      then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("Kakao 토큰 교환 실패 — RestTemplate 예외 발생 시 AUTH_006 예외를 던진다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void loginWithKakao_tokenExchangeFails_throwsAuth006() {
      // given
      OAuthProperties.Kakao kakaoProps = mock(OAuthProperties.Kakao.class);
      given(oAuthProperties.getKakao()).willReturn(kakaoProps);
      given(kakaoProps.getClientId()).willReturn("test_client_id");
      given(kakaoProps.getClientSecret()).willReturn("test_secret");
      given(kakaoProps.getRedirectUri()).willReturn("http://localhost:3000/oauth/kakao");
      // 프로덕션은 토큰 교환 시 exchange(POST, ParameterizedTypeReference) 를 호출하므로 해당 시그니처를 스텁한다
      given(
              restTemplate.exchange(
                  anyString(),
                  eq(HttpMethod.POST),
                  any(),
                  any(ParameterizedTypeReference.class)))
          .willThrow(new RuntimeException("Kakao 서버 오류"));

      // when & then
      assertThatThrownBy(
              () -> authService.loginWithKakao(new KakaoLoginRequest("bad_code"), mockResponse))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.AUTH_006));
    }

    @Test
    @DisplayName("Kakao 이메일 누락 — kakao_account.email 없으면 AUTH_007 예외를 던진다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void loginWithKakao_noEmail_throwsAuth007() {
      // given: email 없이 kakao_account 빈 맵 반환
      OAuthProperties.Kakao kakaoProps = mock(OAuthProperties.Kakao.class);
      given(oAuthProperties.getKakao()).willReturn(kakaoProps);
      given(kakaoProps.getClientId()).willReturn("test_client_id");
      given(kakaoProps.getClientSecret()).willReturn("test_secret");
      given(kakaoProps.getRedirectUri()).willReturn("http://localhost:3000/oauth/kakao");

      ResponseEntity<Map> tokenResponse = mock(ResponseEntity.class);
      given(tokenResponse.getBody()).willReturn(Map.of("access_token", "kakao_access_token"));
      given(
              restTemplate.exchange(
                  anyString(),
                  eq(HttpMethod.POST),
                  any(),
                  any(ParameterizedTypeReference.class)))
          .willReturn(tokenResponse);

      ResponseEntity<Map> userResponse = mock(ResponseEntity.class);
      // kakao_account 존재하지만 email 키 없음
      given(userResponse.getBody()).willReturn(Map.of("id", 123456789L, "kakao_account", Map.of()));
      given(
              restTemplate.exchange(
                  anyString(),
                  eq(HttpMethod.GET),
                  any(),
                  any(ParameterizedTypeReference.class)))
          .willReturn(userResponse);

      // when & then
      assertThatThrownBy(
              () -> authService.loginWithKakao(new KakaoLoginRequest("auth_code"), mockResponse))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.AUTH_007));
    }
  }

  @Nested
  @DisplayName("devLogin()")
  class DevLogin {

    @Test
    @DisplayName("플래그가 꺼져 있으면 사용자 조회 없이 AUTH_008 예외를 던진다")
    void devLogin_disabled_throwsAuth008WithoutRepositoryAccess() {
      // given
      ReflectionTestUtils.setField(authService, "devLoginEnabled", false);

      // when & then
      assertThatThrownBy(() -> authService.devLogin(mockResponse))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.AUTH_008));
      then(userRepository).should(never()).findByEmail(anyString());
      then(jwtProvider).should(never()).createAccessToken(anyString());
    }

    @Test
    @DisplayName("demo@ilgwa.kr 사용자가 있으면 해당 사용자로 OAuth와 동일한 LoginResponse 와 쿠키를 발급한다")
    void devLogin_demoEmailExists_returnsLoginResponseAndRefreshCookie() {
      // given
      ReflectionTestUtils.setField(authService, "devLoginEnabled", true);
      User demoUser = makeUser("usr_01DEMO00000000000000000000", DEMO_EMAIL, DEMO_NICKNAME);

      given(userRepository.findByEmail(DEMO_EMAIL)).willReturn(Optional.of(demoUser));
      stubTokenIssuance(demoUser);

      // when
      LoginResponse result = authService.devLogin(mockResponse);

      // then
      assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
      assertThat(result.email()).isEqualTo(DEMO_EMAIL);
      assertThat(result.nickname()).isEqualTo(DEMO_NICKNAME);
      then(valueOps)
          .should()
          .set(anyString(), eq(demoUser.getId()), eq(1_209_600_000L), eq(TimeUnit.MILLISECONDS));
      then(mockResponse)
          .should()
          .addHeader(eq(HttpHeaders.SET_COOKIE), contains("ilkwa_refresh=" + TEST_REFRESH_TOKEN));
      then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("demo@ilgwa.kr가 없으면 가장 먼저 생성된 사용자로 토큰을 발급한다")
    void devLogin_demoEmailMissing_usesFirstUser() {
      // given
      ReflectionTestUtils.setField(authService, "devLoginEnabled", true);
      User firstUser = makeUser("usr_01FIRST0000000000000000000", TEST_EMAIL, TEST_NICKNAME);

      given(userRepository.findByEmail(DEMO_EMAIL)).willReturn(Optional.empty());
      given(userRepository.findAll(any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(firstUser)));
      stubTokenIssuance(firstUser);

      // when
      LoginResponse result = authService.devLogin(mockResponse);

      // then
      assertThat(result.userId()).isEqualTo(firstUser.getId());
      assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
      then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("사용자가 없으면 데모 사용자를 생성한 뒤 토큰을 발급한다")
    void devLogin_noUsers_createsDemoUser() {
      // given
      ReflectionTestUtils.setField(authService, "devLoginEnabled", true);
      User demoUser = makeUser("usr_01DEMO00000000000000000000", DEMO_EMAIL, DEMO_NICKNAME);

      given(userRepository.findByEmail(DEMO_EMAIL)).willReturn(Optional.empty());
      given(userRepository.findAll(any(Pageable.class))).willReturn(new PageImpl<>(List.of()));
      given(authMapper.toDevUser(DEMO_EMAIL, DEMO_NICKNAME)).willReturn(demoUser);
      given(userRepository.save(demoUser)).willReturn(demoUser);
      stubTokenIssuance(demoUser);

      // when
      LoginResponse result = authService.devLogin(mockResponse);

      // then
      assertThat(result.userId()).isEqualTo(demoUser.getId());
      assertThat(result.email()).isEqualTo(DEMO_EMAIL);
      then(userRepository).should().save(demoUser);
    }
  }

  @Nested
  @DisplayName("refresh()")
  class Refresh {

    @Test
    @DisplayName("유효한 리프레시 쿠키로 갱신 요청하면 새 액세스 토큰을 반환한다")
    void refresh_validCookie_returnsNewAccessToken() {
      // given
      Cookie cookie = new Cookie("ilkwa_refresh", TEST_REFRESH_TOKEN);
      User user = makeUser();
      String newAccessToken = "new.access.token";
      String newRefreshToken = "new.refresh.token";
      TokenRefreshResponse expected =
          TokenRefreshResponse.builder().accessToken(newAccessToken).build();

      given(mockRequest.getCookies()).willReturn(new Cookie[] {cookie});
      given(redisTemplate.hasKey(anyString())).willReturn(false); // USED_RT 없음
      given(valueOps.getAndDelete(anyString())).willReturn(TEST_USER_ID);
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(jwtProvider.createAccessToken(TEST_USER_ID)).willReturn(newAccessToken);
      given(jwtProvider.createRefreshToken(TEST_USER_ID)).willReturn(newRefreshToken);
      given(authMapper.toTokenRefreshResponse(newAccessToken)).willReturn(expected);

      // when
      TokenRefreshResponse result = authService.refresh(mockRequest, mockResponse);

      // then
      assertThat(result.accessToken()).isEqualTo(newAccessToken);
    }

    @Test
    @DisplayName("쿠키가 없으면 AUTH_003 예외를 던진다")
    void refresh_noCookie_throwsAuth003() {
      // given
      given(mockRequest.getCookies()).willReturn(null);

      // when & then
      assertThatThrownBy(() -> authService.refresh(mockRequest, mockResponse))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.AUTH_003));
    }

    @Test
    @DisplayName("ilkwa_refresh 쿠키가 없으면 AUTH_003 예외를 던진다")
    void refresh_missingRefreshCookie_throwsAuth003() {
      // given
      Cookie otherCookie = new Cookie("session_id", "abc123");
      given(mockRequest.getCookies()).willReturn(new Cookie[] {otherCookie});

      // when & then
      assertThatThrownBy(() -> authService.refresh(mockRequest, mockResponse))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.AUTH_003));
    }

    @Test
    @DisplayName("Redis 에 토큰이 없으면(만료·이미 사용됨) AUTH_003 예외를 던진다")
    void refresh_tokenNotInRedis_throwsAuth003() {
      // given
      Cookie cookie = new Cookie("ilkwa_refresh", TEST_REFRESH_TOKEN);
      given(mockRequest.getCookies()).willReturn(new Cookie[] {cookie});
      given(redisTemplate.hasKey(anyString())).willReturn(false); // USED_RT 없음
      given(valueOps.getAndDelete(anyString())).willReturn(null); // RT 없음

      // when & then
      assertThatThrownBy(() -> authService.refresh(mockRequest, mockResponse))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.AUTH_003));
    }

    @Test
    @DisplayName("USED_RT deny key 존재 시 재사용 공격으로 판단해 AUTH_004 예외를 던진다")
    void refresh_usedTokenReuse_throwsAuth004() {
      // given
      Cookie cookie = new Cookie("ilkwa_refresh", TEST_REFRESH_TOKEN);
      given(mockRequest.getCookies()).willReturn(new Cookie[] {cookie});
      given(redisTemplate.hasKey(anyString())).willReturn(true); // USED_RT 존재

      // when & then
      assertThatThrownBy(() -> authService.refresh(mockRequest, mockResponse))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.AUTH_004));
      then(valueOps).should(never()).getAndDelete(anyString());
    }

    @Test
    @DisplayName("Token Rotation — 갱신 시 USED_RT deny key 가 저장된다")
    void refresh_tokenRotation_storesUsedRtKey() {
      // given
      Cookie cookie = new Cookie("ilkwa_refresh", TEST_REFRESH_TOKEN);
      User user = makeUser();
      String newAccessToken = "new.access.token";
      String newRefreshToken = "new.refresh.token";

      given(mockRequest.getCookies()).willReturn(new Cookie[] {cookie});
      given(redisTemplate.hasKey(anyString())).willReturn(false);
      given(valueOps.getAndDelete(anyString())).willReturn(TEST_USER_ID);
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(jwtProvider.createAccessToken(TEST_USER_ID)).willReturn(newAccessToken);
      given(jwtProvider.createRefreshToken(TEST_USER_ID)).willReturn(newRefreshToken);
      given(authMapper.toTokenRefreshResponse(newAccessToken))
          .willReturn(TokenRefreshResponse.builder().accessToken(newAccessToken).build());

      // when
      authService.refresh(mockRequest, mockResponse);

      // then: USED_RT(5분) + 새 RT(refreshExpiryMs) 총 2번 set 호출
      then(valueOps).should().set(anyString(), eq(TEST_USER_ID), eq(5L), eq(TimeUnit.MINUTES));
      then(valueOps)
          .should()
          .set(anyString(), eq(TEST_USER_ID), anyLong(), eq(TimeUnit.MILLISECONDS));
    }
  }

  // withdraw()

  @Nested
  @DisplayName("withdraw()")
  class Withdraw {

    @Test
    @DisplayName("탈퇴 시 status=WITHDRAWN·withdrawnAt 기록 + RT 삭제 + 쿠키 제거")
    void withdraw_softDeletesAndClearsSession() {
      // given
      User user = makeUser();
      Cookie cookie = new Cookie("ilkwa_refresh", TEST_REFRESH_TOKEN);
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(mockRequest.getCookies()).willReturn(new Cookie[] {cookie});

      // when
      authService.withdraw(TEST_USER_ID, mockRequest, mockResponse);

      // then: 소프트 삭제 상태 전이
      assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
      assertThat(user.getWithdrawnAt()).isNotNull();
      // RT 무효화 + 쿠키 클리어(maxAge=0)
      then(redisTemplate).should().delete(anyString());
      then(mockResponse)
          .should()
          .addHeader(eq(HttpHeaders.SET_COOKIE), contains("Max-Age=0"));
    }

    @Test
    @DisplayName("쿠키가 없어도 멱등하게 탈퇴 처리되고 쿠키 클리어 응답을 반환한다")
    void withdraw_noCookie_isIdempotent() {
      // given
      User user = makeUser();
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
      given(mockRequest.getCookies()).willReturn(null);

      // when
      authService.withdraw(TEST_USER_ID, mockRequest, mockResponse);

      // then: 토큰 삭제는 건너뛰되 상태 전이·쿠키 클리어는 수행
      assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
      then(redisTemplate).should(never()).delete(anyString());
      then(mockResponse).should().addHeader(eq(HttpHeaders.SET_COOKIE), contains("Max-Age=0"));
    }

    @Test
    @DisplayName("존재하지 않는 사용자 탈퇴 시 USER_001 예외를 던진다")
    void withdraw_userNotFound_throwsUser001() {
      // given
      given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> authService.withdraw(TEST_USER_ID, mockRequest, mockResponse))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.USER_001));
    }

    @Test
    @DisplayName("탈퇴한 계정으로 Google 재로그인 시 AUTH_009 예외를 던진다")
    void reLogin_withdrawnAccount_throwsAuth009() throws Exception {
      // given: 기존 OAuth 계정이 WITHDRAWN 상태
      GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
      User withdrawn = makeUser();
      withdrawn.withdraw();
      OauthAccount existingAccount = mock(OauthAccount.class);

      GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
      payload.setSubject(GOOGLE_UID);
      payload.setEmail(TEST_EMAIL);
      given(googleIdTokenVerifier.verify("valid_token")).willReturn(mockIdToken);
      given(mockIdToken.getPayload()).willReturn(payload);
      given(
              oauthAccountRepository.findByProviderAndProviderUserId(
                  OauthProvider.GOOGLE, GOOGLE_UID))
          .willReturn(Optional.of(existingAccount));
      given(existingAccount.getUser()).willReturn(withdrawn);

      // when & then
      assertThatThrownBy(
              () -> authService.loginWithGoogle(new GoogleLoginRequest("valid_token"), mockResponse))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AuthErrorCode.AUTH_009));
    }
  }
}
