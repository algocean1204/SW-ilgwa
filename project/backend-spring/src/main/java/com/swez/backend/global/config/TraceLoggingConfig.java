/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.swez.backend.global.logging.TraceIdFilter;

/** TraceId 기반 요청 로깅 필터 설정 */
@Configuration
public class TraceLoggingConfig {

  @Bean
  public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
    FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new TraceIdFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.addUrlPatterns("/*");
    return registration;
  }
}
