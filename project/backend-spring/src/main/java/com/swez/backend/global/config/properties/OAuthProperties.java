/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** application.yml의 app.oauth.* 설정을 바인딩하는 프로퍼티 클래스 */
@Getter
@Setter
@ConfigurationProperties("app.oauth")
public class OAuthProperties {

  private Google google = new Google();
  private Kakao kakao = new Kakao();

  /** Google OAuth 설정 */
  @Getter
  @Setter
  public static class Google {
    /** Google Cloud Console에 등록된 클라이언트 ID */
    private String clientId;
  }

  /** Kakao OAuth 설정 */
  @Getter
  @Setter
  public static class Kakao {
    /** Kakao Developers에 등록된 REST API 키 */
    private String clientId;

    /** 카카오 로그인 보안 강화용 Client Secret */
    private String clientSecret;

    /** Kakao Developers 에 등록된 Redirect URI. 환경마다 달라서 환경변수로 관리한다. */
    private String redirectUri;
  }
}
