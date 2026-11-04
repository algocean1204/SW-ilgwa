/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** application.yml의 app.cors.* 설정을 바인딩하는 프로퍼티 클래스 */
@Getter
@Setter
@ConfigurationProperties("app.cors")
public class CorsProperties {
  /** 허용 출처 목록. 쉼표로 구분한 문자열이 List<String> 으로 자동 파싱된다. */
  private List<String> allowedOrigins;
}
