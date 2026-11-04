/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.swez.backend.global.config.properties.S3Properties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** AWS S3 클라이언트 빈을 등록한다. accessKey 유무에 따라 인증 방식을 나눈다. */
@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

  /**
   * S3Client 빈을 생성한다.
   *
   * <p>accessKey 가 설정되어 있으면 StaticCredentialsProvider, 없으면 DefaultCredentialsProvider (EC2 IAM Role
   * 등) 를 사용한다.
   */
  @Bean
  public S3Client s3Client(S3Properties properties) {
    var builder = S3Client.builder().region(Region.of(properties.getRegion()));

    if (properties.getAccessKey() != null && !properties.getAccessKey().isBlank()) {
      /* 명시적 키가 있으면 정적 자격증명 사용 */
      builder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
    } else {
      /* 키가 없으면 IAM Role / 환경변수 등 기본 자격증명 체인 사용 */
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }

    if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
      /* MinIO 같은 S3 호환 스토리지는 명시 endpoint와 path-style 접근을 사용한다 */
      builder.endpointOverride(URI.create(properties.getEndpoint()));
      builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }

    return builder.build();
  }
}
