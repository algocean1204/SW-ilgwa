/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** application.yml의 swagger.* 설정을 바인딩하는 프로퍼티 클래스 */
@Getter
@Setter
@ConfigurationProperties("swagger")
public class SwaggerProperties {
  /** API 문서 제목 */
  private String title;

  /** API 문서 설명 */
  private String description;

  /** API 버전 */
  private String version;
}
