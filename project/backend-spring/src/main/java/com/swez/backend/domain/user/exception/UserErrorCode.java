/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 사용자 도메인 에러 코드. USER_001·002는 AuthErrorCode 에 있다. */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements BaseErrorCode {
  USER_004(HttpStatus.CONFLICT, "USER_004", "이미 온보딩이 완료된 사용자입니다."),
  USER_005(HttpStatus.CONFLICT, "USER_005", "이미 텔레그램이 연동된 계정입니다."),
  USER_006(HttpStatus.BAD_REQUEST, "USER_006", "텔레그램 연동 토큰이 만료되었거나 유효하지 않습니다."),
  USER_007(HttpStatus.CONFLICT, "USER_007", "해당 텔레그램 계정은 이미 다른 사용자에게 연동되어 있습니다."),
  USER_008(HttpStatus.CONFLICT, "USER_008", "해당 사용자는 이미 텔레그램이 연동된 상태입니다."),
  USER_009(HttpStatus.BAD_REQUEST, "USER_009", "프로필 이미지 파일이 비어있습니다."),
  USER_010(HttpStatus.BAD_REQUEST, "USER_010", "지원하지 않는 이미지 형식입니다. (jpg, png, webp만 허용)"),
  USER_011(HttpStatus.BAD_REQUEST, "USER_011", "프로필 이미지 파일 크기가 5MB를 초과합니다."),
  USER_012(HttpStatus.INTERNAL_SERVER_ERROR, "USER_012", "프로필 이미지 업로드에 실패했습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
