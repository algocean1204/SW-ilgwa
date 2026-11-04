/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * ExamForge 전용 RestClient 빈 설정.
 *
 * <p>{@code app.fastapi.base-url}·{@code app.fastapi.api-key} 두 프로퍼티가 모두 존재하는 경우에만 {@code
 * examForgeRestClient} 빈을 등록한다. 설정이 없는 환경(예: 로컬에 FastAPI 없음)에서는 어댑터·서비스 빈도 만들어지지 않는다.
 *
 * <p>base-url·api-key는 {@code app.fastapi.*} 값을 그대로 쓰므로 {@code APP_FASTAPI_BASE_URL}·{@code
 * FASTAPI_API_KEY} 환경변수만 설정하면 된다.
 *
 * <p>HTTP 팩토리는 {@link SimpleClientHttpRequestFactory}(java.net.HttpURLConnection 기반)로 고정한다.
 * {@code ClientHttpRequestFactoryBuilder.detect()}는 클래스패스에 따라 Apache HttpComponents 4.x 등을 고를 수
 * 있는데, 그 팩토리는 {@code contentType()} 없이는 body 를 직렬화하지 못한다. Simple 팩토리는 Jackson 이 Content-Type
 * 을 설정하면 body 를 그대로 보낸다.
 */
@Configuration
@EnableConfigurationProperties(ExamForgeFastApiProperties.class)
@ConditionalOnProperty(
    prefix = "app.fastapi",
    name = {"base-url", "api-key"})
public class ExamForgeRestClientConfig {

  @Bean
  public RestClient examForgeRestClient(
      RestClient.Builder builder, ExamForgeFastApiProperties properties) {
    // HttpURLConnection 기반 팩토리는 Content-Type을 명시하면 body를 빠짐없이 직렬화한다.
    // detect() 대신 이 팩토리를 고정해 환경별 동작 차이를 없앤다.
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
    factory.setReadTimeout((int) properties.readTimeout().toMillis());

    return builder
        .baseUrl(properties.baseUrl())
        .defaultHeader("X-API-Key", properties.apiKey())
        .requestFactory(factory)
        .build();
  }
}
