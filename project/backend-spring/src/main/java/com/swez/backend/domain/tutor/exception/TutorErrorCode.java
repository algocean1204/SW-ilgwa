/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 튜터 도메인 에러 코드 */
@Getter
@RequiredArgsConstructor
public enum TutorErrorCode implements BaseErrorCode {
  TUT_001(HttpStatus.NOT_FOUND, "TUT_001", "튜터를 찾을 수 없습니다."),
  TUT_002(HttpStatus.FORBIDDEN, "TUT_002", "기본 제공 튜터는 수정하거나 삭제할 수 없습니다."),
  TUT_003(HttpStatus.FORBIDDEN, "TUT_003", "Pro 이상 구독이 필요한 기능입니다."),
  TUT_004(HttpStatus.BAD_GATEWAY, "TUT_004", "미리보기 생성에 실패했습니다."),
  TUT_005(HttpStatus.BAD_REQUEST, "TUT_005", "음성 샘플 파일이 비어있습니다."),
  TUT_006(HttpStatus.BAD_REQUEST, "TUT_006", "지원하지 않는 파일 형식입니다. WAV 또는 MP3 파일만 업로드 가능합니다."),
  TUT_007(HttpStatus.BAD_REQUEST, "TUT_007", "파일 크기가 10MB를 초과합니다."),
  TUT_008(HttpStatus.INTERNAL_SERVER_ERROR, "TUT_008", "파일 업로드에 실패했습니다."),
  TUT_009(HttpStatus.BAD_REQUEST, "TUT_009", "프로필 이미지 파일이 비어있습니다."),
  TUT_010(HttpStatus.BAD_REQUEST, "TUT_010", "지원하지 않는 이미지 형식입니다. JPG 또는 PNG 파일만 업로드 가능합니다."),
  TUT_011(HttpStatus.BAD_REQUEST, "TUT_011", "이미지 크기가 5MB를 초과합니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
