/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 토스페이먼츠 설정. TossPaymentProperties 를 빈으로 등록한다. */
@Configuration
@EnableConfigurationProperties(TossPaymentProperties.class)
public class TossPaymentConfig {}
