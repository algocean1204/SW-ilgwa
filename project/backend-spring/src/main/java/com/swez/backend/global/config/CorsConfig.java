/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.swez.backend.global.config.properties.CorsProperties;

import lombok.RequiredArgsConstructor;

/**
 * CORS 설정. 웹 프론트(다른 origin)에서 API 를 호출할 수 있도록 허용 출처를 등록한다. HTTP-Only Cookie 기반
 * refreshToken 을 주고받으려고 allowCredentials 를 켠다.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

  private final CorsProperties properties;

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(properties.getAllowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    // refreshToken HTTP-Only Cookie 송수신을 위해 자격 증명 포함 요청 허용
    config.setAllowCredentials(true);
    // preflight 응답을 1시간 캐싱해 OPTIONS 호출 횟수를 줄인다
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
