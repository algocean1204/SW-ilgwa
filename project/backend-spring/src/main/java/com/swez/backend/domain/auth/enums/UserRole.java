/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** 사용자 권한. Spring Security ROLE_* 권한으로 변환된다. */
@Schema(description = "사용자 권한")
public enum UserRole {
  USER,
  ADMIN,
  SUPER_ADMIN
}
