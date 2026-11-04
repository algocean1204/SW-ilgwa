/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.swez.backend.global.logging.TraceIdRestTemplateInterceptor;

/** 외부 API 호출용 RestTemplate 빈 설정. Kakao OAuth 토큰 교환 등에서 쓴다. */
@Configuration
public class RestTemplateConfig {

  @Bean
  @Primary
  public RestTemplate restTemplate() {
    RestTemplate restTemplate = new RestTemplate();
    restTemplate.getInterceptors().add(new TraceIdRestTemplateInterceptor());
    return restTemplate;
  }

  @Bean(name = "fastapiRestTemplate")
  public RestTemplate fastapiRestTemplate(
      @Value("${app.fastapi.api-key:${FASTAPI_API_KEY:}}") String fastapiApiKey) {
    /*
     * FastAPI 전용 빈이다. 공유 빈과 분리해 X-API-Key 를 이 빈에만 붙인다.
     * 외부 결제/텔레그램으로 키가 유출되지 않도록 공유 restTemplate에는 인증 헤더를 추가하지 않는다.
     * app.fastapi.api-key 프로퍼티 미설정 시 FASTAPI_API_KEY 환경변수를 직접 폴백으로 사용한다.
     */

    /*
     * 타임아웃은 세 겹으로 둔다.
     *   FastAPI LLM 호출 상한     ≈ 180s  (채팅·채점 최대 응답 17s보다 넉넉하게)
     *   Spring 읽기 타임아웃     = 200s  (여기서 설정, LLM 상한 180s 직후 회수)
     *   nginx proxy_read_timeout = 300s  (upstream 설정)
     *
     * 180 < 200 < 300 순서라서 LLM 상한이 지난 직후 Spring이 nginx보다 먼저 소켓을 끊는다.
     * FastAPI가 행(hang) 상태에 빠져도 Spring 스레드 풀이 최대 200s 내에 반환된다.
     * new RestTemplate() 기본값은 readTimeout = -1(무한)이라 스레드 풀이 고갈될 수 있다.
     */
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(10));
    factory.setReadTimeout(Duration.ofSeconds(200));

    RestTemplate restTemplate = new RestTemplate(factory);
    restTemplate.getInterceptors().add(new TraceIdRestTemplateInterceptor());
    restTemplate.getInterceptors().add(new FastApiAuthInterceptor(fastapiApiKey));
    return restTemplate;
  }
}
