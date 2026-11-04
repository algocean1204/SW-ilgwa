/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config.properties;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

/** application.yml의 app.s3.* 설정을 바인딩하는 프로퍼티 클래스 */
@Validated
@Getter
@Setter
@ConfigurationProperties("app.s3")
public class S3Properties {
  /** S3 버킷 이름 (필수) */
  @NotBlank private String bucket;

  /** S3 리전 (필수, 기본값: ap-northeast-2) */
  @NotBlank private String region;

  /** S3 액세스 키. 비어 있으면 DefaultCredentialsProvider 를 쓴다. */
  private String accessKey;

  /** S3 시크릿 키. 비어 있으면 DefaultCredentialsProvider 를 쓴다. */
  private String secretKey;

  /** S3 호환 스토리지 endpoint. 비어 있으면 AWS 기본 endpoint 를 쓴다. */
  private String endpoint;

  /**
   * 업로드 파일의 공개 접근 URL 베이스. 비어 있으면 AWS 가상호스트 URL 형식을 쓴다.
   *
   * <p>MinIO 등 path-style 스토리지에서 브라우저가 실제 접근 가능한 베이스를 지정한다. 예: http://localhost:9000/sw-ez-media
   */
  private String publicUrlBase;
}
