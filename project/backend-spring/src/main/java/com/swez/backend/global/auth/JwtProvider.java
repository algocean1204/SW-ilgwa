/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.swez.backend.global.config.properties.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT 액세스·리프레시 토큰을 생성하고 검증한다. 서명 알고리즘은 HMAC-SHA256 이며 키는 JwtProperties.secret 에서 UTF-8 바이트로 유도한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

  private final JwtProperties properties;
  private SecretKey key;

  @PostConstruct
  void init() {
    this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
  }

  /** 액세스 토큰을 만든다. subject 에 userId(ULID 문자열)를 담는다. */
  public String createAccessToken(String userId) {
    return buildToken(userId, properties.getAccessExpiryMs());
  }

  /** 리프레시 토큰 생성 */
  public String createRefreshToken(String userId) {
    return buildToken(userId, properties.getRefreshExpiryMs());
  }

  /** 토큰이 유효하면 true, 만료·변조면 false 를 반환한다. 실패 원인은 warn 로그로 남긴다. */
  public boolean validate(String token) {
    try {
      parse(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("[JwtProvider] 토큰 검증 실패: {}", e.getMessage());
      return false;
    }
  }

  /** 토큰에서 userId 추출. validate() 통과 후에만 호출해야 한다 */
  public String getUserId(String token) {
    return parse(token).getSubject();
  }

  private Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  private String buildToken(String userId, long expiryMs) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(expiryMs)))
        .signWith(key)
        .compact();
  }
}
