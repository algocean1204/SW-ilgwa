/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** application.yml의 app.jwt.* 설정을 바인딩하는 프로퍼티 클래스 */
@Getter
@Setter
@ConfigurationProperties("app.jwt")
public class JwtProperties {
  /** HMAC 서명용 비밀키 (최소 32자 랜덤 문자열) */
  private String secret;

  /** 액세스 토큰 만료 시간 (ms). 기본 30분 */
  private long accessExpiryMs;

  /** 리프레시 토큰 만료 시간 (ms). 기본 14일 */
  private long refreshExpiryMs;
}
