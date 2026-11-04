/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config;

import java.util.Collections;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.swez.backend.global.config.properties.OAuthProperties;

import lombok.RequiredArgsConstructor;

/** Google ID Token 검증기 빈 설정 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(OAuthProperties.class)
public class GoogleOAuthConfig {

  private final OAuthProperties oAuthProperties;

  /** Google ID Token 검증기. audience 는 Google Cloud Console 에 등록된 클라이언트 ID 로 설정되며, 공개키는 자동 캐싱된다. */
  @Bean
  public GoogleIdTokenVerifier googleIdTokenVerifier() {
    return new GoogleIdTokenVerifier.Builder(
            new NetHttpTransport(), GsonFactory.getDefaultInstance())
        .setAudience(Collections.singletonList(oAuthProperties.getGoogle().getClientId()))
        .build();
  }
}
