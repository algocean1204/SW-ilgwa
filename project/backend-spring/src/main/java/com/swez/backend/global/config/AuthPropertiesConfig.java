/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.swez.backend.global.config.properties.AuthCookieProperties;

/** 쿠키 설정처럼 OAuth 와 무관한 인증 ConfigurationProperties 를 등록한다. */
@Configuration
@EnableConfigurationProperties(AuthCookieProperties.class)
public class AuthPropertiesConfig {}
