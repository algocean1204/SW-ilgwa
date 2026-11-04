/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 토스페이먼츠 API 설정. application.yml 의 app.toss 섹션을 바인딩한다. */
@ConfigurationProperties(prefix = "app.toss")
public record TossPaymentProperties(String clientKey, String secretKey, String baseUrl) {}
