/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 구독 도메인 에러 코드 */
@Getter
@RequiredArgsConstructor
public enum SubscriptionErrorCode implements BaseErrorCode {
  SUB_001(HttpStatus.BAD_GATEWAY, "SUB_001", "토스페이먼츠 결제 처리에 실패했습니다."),
  SUB_002(HttpStatus.NOT_FOUND, "SUB_002", "활성 구독이 존재하지 않습니다."),
  SUB_003(HttpStatus.BAD_REQUEST, "SUB_003", "유효하지 않은 플랜입니다."),
  SUB_004(HttpStatus.CONFLICT, "SUB_004", "이미 해당 플랜을 구독 중입니다."),
  SUB_005(HttpStatus.BAD_REQUEST, "SUB_005", "결제 금액이 일치하지 않습니다."),
  SUB_006(HttpStatus.BAD_REQUEST, "SUB_006", "현재 플랜 이하로는 다운그레이드할 수 없습니다."),
  SUB_007(HttpStatus.BAD_REQUEST, "SUB_007", "Free 플랜은 결제가 필요하지 않습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
