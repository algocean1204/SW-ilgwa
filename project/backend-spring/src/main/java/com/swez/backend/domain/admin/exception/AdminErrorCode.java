/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminErrorCode implements BaseErrorCode {
  ADMIN_001(HttpStatus.BAD_REQUEST, "ADMIN_001", "텔레그램 chat_id가 필요한 요청입니다."),
  ADMIN_002(HttpStatus.CONFLICT, "ADMIN_002", "이미 다른 사용자에게 연결된 텔레그램 chat_id입니다."),
  ADMIN_003(HttpStatus.BAD_REQUEST, "ADMIN_003", "수정할 텔레그램 credential 값이 없습니다."),
  ADMIN_004(HttpStatus.NOT_FOUND, "ADMIN_004", "텔레그램 chat_id에 연결된 사용자를 찾을 수 없습니다."),
  ADMIN_005(HttpStatus.BAD_REQUEST, "ADMIN_005", "발송할 메시지가 비어있습니다."),
  ADMIN_006(HttpStatus.NOT_FOUND, "ADMIN_006", "텔레그램 봇 토큰이 설정되지 않았습니다."),
  ADMIN_007(HttpStatus.CONFLICT, "ADMIN_007", "이미 등록된 수신자 chat_id입니다."),
  ADMIN_008(HttpStatus.NOT_FOUND, "ADMIN_008", "수신자를 찾을 수 없습니다."),
  ADMIN_009(HttpStatus.NOT_FOUND, "ADMIN_009", "알림 규칙을 찾을 수 없습니다."),
  ADMIN_010(HttpStatus.FORBIDDEN, "ADMIN_010", "권한 없는 알림 규칙입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
