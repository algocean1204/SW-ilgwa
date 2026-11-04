/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** application.yml의 app.auth.cookie.* 설정을 바인딩하는 프로퍼티 클래스 */
@Getter
@Setter
@ConfigurationProperties("app.auth.cookie")
public class AuthCookieProperties {
  /** Secure 쿠키 여부. 운영(HTTPS)은 true, 로컬(HTTP)은 false */
  private boolean secure = true;

  /** SameSite 정책 (Strict | Lax | None) */
  private String sameSite = "Strict";

  /** 쿠키 도메인. 설정하지 않으면 현재 도메인에만 적용된다. */
  private String domain = "";
}
