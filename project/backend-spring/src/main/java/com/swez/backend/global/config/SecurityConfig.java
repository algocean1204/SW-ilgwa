/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.swez.backend.global.auth.InternalTokenFilter;
import com.swez.backend.global.auth.JwtAccessDeniedHandler;
import com.swez.backend.global.auth.JwtAuthenticationEntryPoint;
import com.swez.backend.global.auth.JwtAuthenticationFilter;
import com.swez.backend.global.config.properties.JwtProperties;

import lombok.RequiredArgsConstructor;

/**
 * Spring Security 설정. JWT 기반 stateless 인증 체인을 조립한다.
 *
 * <p>@EnableMethodSecurity 로 @PreAuthorize 등 메서드 레벨 권한을 지원한다. JwtProperties 를 이곳에서 빈으로
 * 등록해(SwaggerConfig 가 SwaggerProperties 를 등록하는 방식과 같다) JwtProvider 가 주입받을 수 있게 한다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

  private final CorsConfigurationSource corsConfigurationSource;
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final InternalTokenFilter internalTokenFilter;
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
  private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    configureFilters(http);
    configureExceptionHandling(http);
    configureAuthorization(http);
    return http.build();
  }

  /** CSRF·CORS·세션·JWT 필터 설정 */
  private void configureFilters(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(internalTokenFilter, JwtAuthenticationFilter.class);
  }

  /** 401 / 403 응답을 ApiResponse 포맷으로 직렬화하는 핸들러 연결 */
  private void configureExceptionHandling(HttpSecurity http) throws Exception {
    http.exceptionHandling(
        ex ->
            ex.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler));
  }

  /** 경로별 권한. 공개 경로 외에는 모두 인증이 필요하다. */
  private void configureAuthorization(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers(
                    "/auth/login/**",
                    "/auth/dev-login", // 로컬 데모 로그인. 실제 활성화 여부는 AuthService 플래그가 정한다
                    "/auth/refresh",
                    "/auth/logout", // 만료된 액세스 토큰으로도 호출 가능해야 하므로 refresh 와 동일하게 공개한다
                    "/health",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/error",
                    "/internal/**", // InternalTokenFilter가 별도 인증 처리
                    "/webhook/**", // 텔레그램 웹훅은 secret_token 헤더로 따로 인증한다
                    "/subscription/plans", // 구독 플랜 목록은 비인증 공개 API
                    "/coins/bundles") // 코인 번들 목록은 비인증 공개 API
                .permitAll()
                .anyRequest()
                .authenticated());
  }

  /** BCrypt 인코더. AuthService 비밀번호 해싱·검증에 주입된다. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** 인증 매니저. 이메일 로그인 때 AuthenticationProvider 에 위임하는 데 쓴다. */
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }
}
