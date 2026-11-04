/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.exception.model;

import org.springframework.http.HttpStatus;

/** 모든 에러 코드 enum이 구현해야 하는 계약 인터페이스 */
public interface BaseErrorCode {
  HttpStatus getHttpStatus();

  String getCode();

  String getMessage();
}
