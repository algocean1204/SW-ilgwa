/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NoteErrorCode implements BaseErrorCode {
  NTE_001(HttpStatus.NOT_FOUND, "NTE_001", "노트를 찾을 수 없습니다."),
  NTE_002(HttpStatus.FORBIDDEN, "NTE_002", "본인의 노트만 수정·삭제할 수 있습니다."),
  NTE_003(HttpStatus.BAD_REQUEST, "NTE_003", "텔레그램 계정이 연동되어 있지 않습니다."),
  NTE_004(HttpStatus.BAD_GATEWAY, "NTE_004", "텔레그램 메시지 전송에 실패했습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
