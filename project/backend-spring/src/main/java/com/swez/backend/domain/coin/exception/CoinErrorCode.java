/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 코인 도메인 에러 코드 */
@Getter
@RequiredArgsConstructor
public enum CoinErrorCode implements BaseErrorCode {
  CON_001(HttpStatus.BAD_REQUEST, "CON_001", "코인 잔액이 부족합니다."),
  CON_002(HttpStatus.CONFLICT, "CON_002", "이미 해제된 슬라이드입니다."),
  CON_003(HttpStatus.NOT_FOUND, "CON_003", "존재하지 않는 리소스입니다."),
  CON_004(HttpStatus.BAD_REQUEST, "CON_004", "코스를 완강하지 않았습니다."),
  CON_005(HttpStatus.BAD_GATEWAY, "CON_005", "토스페이먼츠 결제 처리에 실패했습니다."),
  CON_006(HttpStatus.BAD_REQUEST, "CON_006", "결제 금액이 일치하지 않습니다."),
  CON_007(HttpStatus.BAD_REQUEST, "CON_007", "유효하지 않은 번들 ID입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
