/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** JPA Auditing 을 켜서 BaseTimeEntity 의 @CreatedDate / @LastModifiedDate 를 자동으로 채운다. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {}
