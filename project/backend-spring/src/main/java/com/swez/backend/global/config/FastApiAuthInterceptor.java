/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

/**
 * FastAPI 호출에 내부 인증용 API 키 헤더를 부착하는 인터셉터.
 *
 * <p>빈 생성 시점에 주입받은 키가 비어 있으면 요청 시마다 FASTAPI_API_KEY 환경변수를 직접 조회해 Spring @Value 프로퍼티 바인딩 타이밍 문제를
 * 우회한다.
 */
public class FastApiAuthInterceptor implements ClientHttpRequestInterceptor {

  private static final String API_KEY_HEADER = "X-API-Key";

  /** 빈 초기화 시 주입받은 키. 비어 있으면 요청마다 환경변수에서 읽는다. */
  private final String injectedApiKey;

  public FastApiAuthInterceptor(String apiKey) {
    this.injectedApiKey = apiKey;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    // 1순위: 빈 초기화 시 주입받은 키 사용
    String key = injectedApiKey;
    // 2순위: 주입 키가 비어 있으면 환경변수에서 직접 읽는다 (Spring 프로퍼티 타이밍 문제 우회)
    if (!StringUtils.hasText(key)) {
      key = System.getenv("FASTAPI_API_KEY");
    }
    if (StringUtils.hasText(key)) {
      request.getHeaders().set(API_KEY_HEADER, key);
    }
    /*
     * 인증을 끈 개발 환경과 호환되도록 두 경로가 모두 비어 있으면 헤더를 붙이지 않는다.
     */
    return execution.execute(request, body);
  }
}
