/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.auth;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.global.logging.TraceId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모든 요청에서 Authorization 헤더의 Bearer 토큰을 파싱해 SecurityContext 에 인증 정보를 주입한다. 토큰이 없거나 검증 실패 시
 * SecurityContext 를 비운 채 체인을 통과시키며, 보호 경로라면 ExceptionTranslationFilter 가
 * JwtAuthenticationEntryPoint 를 호출해 401 을 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  /** DB 쓰기 빈도를 줄이려는 lastSeenAt 갱신 간격. 5분 안의 재요청은 갱신하지 않는다. */
  private static final Duration LAST_SEEN_UPDATE_INTERVAL = Duration.ofMinutes(5);

  private final JwtProvider jwtProvider;
  private final UserRepository userRepository;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = resolveToken(request);

    if (StringUtils.hasText(token) && jwtProvider.validate(token)) {
      String userId = jwtProvider.getUserId(token);
      userRepository
          .findById(userId)
          .ifPresent(
              user -> {
                // 탈퇴·정지 계정의 남은 액세스 토큰은 무효로 본다. SecurityContext 에 넣지 않고(미인증)
                // 통과시켜 보호 경로에서 EntryPoint 가 401 을 반환하게 한다. lastSeenAt 갱신보다 먼저 둬
                // 불필요한 DB write 도 막는다.
                if (user.isWithdrawn() || user.isSuspended()) {
                  log.debug(
                      "[JwtAuthenticationFilter] 차단된 계정 — 인증 미주입 | userId: {}, status: {}",
                      userId,
                      user.getStatus());
                  return;
                }

                updateLastSeenIfNeeded(user);

                UserPrincipal principal = toPrincipal(user);
                MDC.put(TraceId.MDC_USER_ID, userId);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug(
                    "[JwtAuthenticationFilter] SecurityContext 인증 주입 완료 — userId: {}", userId);
              });
    }

    filterChain.doFilter(request, response);
  }

  /** 마지막 접속 시각을 갱신한다. 5분 안에는 다시 갱신하지 않아 DB 부하를 줄인다. */
  protected void updateLastSeenIfNeeded(User user) {
    LocalDateTime now = LocalDateTime.now();
    if (user.getLastSeenAt() == null
        || Duration.between(user.getLastSeenAt(), now).compareTo(LAST_SEEN_UPDATE_INTERVAL) > 0) {
      user.updateLastSeenAt();
      userRepository.save(user);
    }
  }

  private UserPrincipal toPrincipal(User user) {
    return UserPrincipal.builder()
        .userId(user.getId())
        .email(user.getEmail())
        .role(user.getRole())
        .status(user.getStatus())
        .build();
  }

  /** Authorization 헤더에서 Bearer 접두사를 제거해 순수 토큰만 추출 */
  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }
}
