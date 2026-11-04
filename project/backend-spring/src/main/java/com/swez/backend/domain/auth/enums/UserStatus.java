/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** 사용자 계정 상태 */
@Schema(description = "사용자 계정 상태")
public enum UserStatus {
  ACTIVE,
  SUSPENDED,
  /** 회원 탈퇴(소프트 삭제). 데이터는 보존하고 로그인·재가입을 막는다. */
  WITHDRAWN
}
