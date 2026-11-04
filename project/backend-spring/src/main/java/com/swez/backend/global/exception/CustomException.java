/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.exception;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;

/** 모든 비즈니스 예외의 공통 타입. 도메인 서비스에서 throw new CustomException(ErrorCode.XXX) 로 던진다. */
@Getter
public class CustomException extends RuntimeException {

  private final BaseErrorCode errorCode;

  public CustomException(BaseErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }
}
