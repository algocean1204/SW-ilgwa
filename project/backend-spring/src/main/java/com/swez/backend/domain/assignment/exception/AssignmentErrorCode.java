/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.assignment.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 과제 도메인 에러 코드 */
@Getter
@RequiredArgsConstructor
public enum AssignmentErrorCode implements BaseErrorCode {
  ASG_001(HttpStatus.NOT_FOUND, "ASG_001", "과제를 찾을 수 없습니다."),
  ASG_002(HttpStatus.BAD_REQUEST, "ASG_002", "마감일이 지난 과제입니다."),
  ASG_003(HttpStatus.CONFLICT, "ASG_003", "이미 제출된 과제입니다."),
  ASG_004(HttpStatus.NOT_FOUND, "ASG_004", "채점 결과가 아직 없습니다."),
  ASG_005(HttpStatus.NOT_FOUND, "ASG_005", "제출 기록을 찾을 수 없습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
