/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.swez.backend.global.config.properties.S3Properties;

import software.amazon.awssdk.services.s3.S3Client;

@DisplayName("S3Config 단위 테스트")
class S3ConfigTest {

  private final S3Config config = new S3Config();

  @Test
  @DisplayName("endpoint가 있으면 S3Client에 endpoint override를 적용한다")
  void s3Client_endpointConfigured_appliesEndpointOverride() {
    S3Properties properties = baseProperties();
    properties.setEndpoint("http://minio:9000");

    try (S3Client client = config.s3Client(properties)) {
      assertThat(client.serviceClientConfiguration().endpointOverride())
          .contains(URI.create("http://minio:9000"));
    }
  }

  @Test
  @DisplayName("endpoint가 비어있으면 AWS 기본 endpoint 동작을 유지한다")
  void s3Client_endpointBlank_keepsAwsDefaultEndpoint() {
    S3Properties properties = baseProperties();
    properties.setEndpoint("");

    try (S3Client client = config.s3Client(properties)) {
      assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
    }
  }

  private S3Properties baseProperties() {
    S3Properties properties = new S3Properties();
    properties.setBucket("test-bucket");
    properties.setRegion("ap-northeast-2");
    properties.setAccessKey("test-access-key");
    properties.setSecretKey("test-secret-key");
    return properties;
  }
}
