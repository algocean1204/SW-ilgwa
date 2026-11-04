/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 인증 도메인 에러 코드. AUTH_001/002 는 필터 레이어용이라 GlobalErrorCode 에 있다. */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

  // 사용자 조회 / 중복
  USER_001(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),
  USER_002(HttpStatus.CONFLICT, "USER_002", "이미 사용 중인 이메일입니다."),

  // 리프레시 토큰
  AUTH_003(HttpStatus.UNAUTHORIZED, "AUTH_003", "리프레시 토큰이 유효하지 않거나 만료되었습니다."),
  AUTH_004(HttpStatus.UNAUTHORIZED, "AUTH_004", "이미 사용된 리프레시 토큰입니다."),

  // OAuth
  AUTH_005(HttpStatus.UNAUTHORIZED, "AUTH_005", "Google 인증에 실패했습니다."),
  AUTH_006(HttpStatus.UNAUTHORIZED, "AUTH_006", "Kakao 인증에 실패했습니다."),
  AUTH_007(HttpStatus.UNAUTHORIZED, "AUTH_007", "OAuth 이메일 제공 동의가 필요합니다."),

  // 로컬 데모 로그인
  AUTH_008(HttpStatus.FORBIDDEN, "AUTH_008", "개발 로그인은 비활성화되어 있습니다."),

  // 회원 탈퇴
  AUTH_009(HttpStatus.FORBIDDEN, "AUTH_009", "탈퇴한 계정입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
