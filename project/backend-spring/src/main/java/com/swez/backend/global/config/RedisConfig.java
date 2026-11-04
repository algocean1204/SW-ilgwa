/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 연결 설정. 커넥션 팩토리는 spring-boot-starter-data-redis 자동 설정에 맡기고 여기서는 key/value 직렬화를 String 으로 고정한
 * RedisTemplate 만 등록한다.
 */
@Configuration
public class RedisConfig {

  /**
   * RefreshToken, 세션 등 문자열 기반 데이터를 저장하는 기본 템플릿. key 와 value 를 모두 StringRedisSerializer 로
   * 직렬화해 바이트 배열 대신 사람이 읽을 수 있는 형태로 저장한다.
   */
  @Bean
  public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(new StringRedisSerializer());
    return template;
  }
}
