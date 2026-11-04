/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** application.yml의 swagger.server.* 설정을 바인딩하는 프로퍼티 클래스 */
@Getter
@Setter
@ConfigurationProperties("swagger.server")
public class SwaggerServerProperties {
  /** 서버 URL (예: http://localhost:8080) */
  private String url;

  /** 서버 환경 설명 (예: 로컬 개발 서버) */
  private String description;
}
