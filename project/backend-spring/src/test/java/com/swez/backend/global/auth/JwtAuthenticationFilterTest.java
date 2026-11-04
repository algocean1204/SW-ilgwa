/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.enums.UserStatus;
import com.swez.backend.domain.auth.repository.UserRepository;

@DisplayName("JwtAuthenticationFilter 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

  @Mock private JwtProvider jwtProvider;
  @Mock private UserRepository userRepository;

  private JwtAuthenticationFilter filter;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain filterChain;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String TOKEN = "valid.access.token";

  @BeforeEach
  void setUp() {
    filter = new JwtAuthenticationFilter(jwtProvider, userRepository);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    filterChain = mock(FilterChain.class);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  /** 테스트용 User 를 원하는 상태로 생성한다. */
  private User makeUser(UserStatus status) {
    return User.builder()
        .id(USER_ID)
        .email("test@example.com")
        .nickname("테스터")
        .status(status)
        .subscriptionStatus(SubscriptionStatus.FREE)
        .build();
  }

  /** Bearer 토큰이 검증을 통과하고 userId 로 매핑되도록 공통 스텁한다. */
  private void givenValidToken() {
    given(request.getHeader(HttpHeaders.AUTHORIZATION)).willReturn("Bearer " + TOKEN);
    given(jwtProvider.validate(TOKEN)).willReturn(true);
    given(jwtProvider.getUserId(TOKEN)).willReturn(USER_ID);
  }

  @Test
  @DisplayName("ACTIVE 사용자 — SecurityContext 에 인증이 주입되고 체인이 진행된다")
  void active_injectsAuthentication() throws Exception {
    // given
    givenValidToken();
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(makeUser(UserStatus.ACTIVE)));

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    then(filterChain).should().doFilter(request, response);
  }

  @Test
  @DisplayName("WITHDRAWN 사용자 — 인증 미주입 + lastSeen 갱신(save) 미호출 + 체인은 진행")
  void withdrawn_skipsAuthentication() throws Exception {
    // given
    givenValidToken();
    given(userRepository.findById(USER_ID))
        .willReturn(Optional.of(makeUser(UserStatus.WITHDRAWN)));

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then: 미인증으로 통과(보호 경로면 EntryPoint 가 401)
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    then(userRepository).should(never()).save(any(User.class));
    then(filterChain).should().doFilter(request, response);
  }

  @Test
  @DisplayName("SUSPENDED 사용자 — 인증이 주입되지 않는다")
  void suspended_skipsAuthentication() throws Exception {
    // given
    givenValidToken();
    given(userRepository.findById(USER_ID))
        .willReturn(Optional.of(makeUser(UserStatus.SUSPENDED)));

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    then(userRepository).should(never()).save(any(User.class));
    then(filterChain).should().doFilter(request, response);
  }

  @Test
  @DisplayName("토큰이 없으면 인증 없이 체인을 통과한다")
  void noToken_passesThrough() throws Exception {
    // given
    given(request.getHeader(HttpHeaders.AUTHORIZATION)).willReturn(null);

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    then(userRepository).should(never()).findById(any());
    then(filterChain).should().doFilter(request, response);
  }
}
